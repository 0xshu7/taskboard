package com.example.taskboard.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.Granularity;
import com.example.taskboard.domain.ProposalCard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// ai.mock=false のときに使う、本物の Gemini を呼ぶクラス
@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "false")
public class RealProposalGenerator implements ProposalGenerator {

	private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

	private final RestClient rest = RestClient.create();

	// JSON を Java のオブジェクトに変換する道具。Spring Boot に最初から入っている
	private final ObjectMapper mapper = new ObjectMapper();

	@Value("${ai.model}")
	private String model;

	@Value("${ai.api-key}")
	private String apiKey;

	@Override
	public String sourceName() {
		return "AI";

	}

	@Value("${ai.max-attempts:3}")
	private int maxAttempts;

	@Override
	public List<ProposalCard> generate(String prompt) {

		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("GEMINI_API_KEY が設定されていません");
		}

		Map<String, Object> body = Map.of(
				"contents", List.of(
						Map.of("parts", List.of(Map.of("text", prompt)))),
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"responseSchema", schema(),
						"thinkingConfig", Map.of("thinkingLevel", "low"), // ← ここを変更
						"maxOutputTokens", 800));

		RuntimeException lastError = null;

		// 混雑（503）や回数制限（429）は時間をおけば直ることがあるので、何度か試す
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {

			try {
				Map<?, ?> response = rest.post()
						.uri(String.format(ENDPOINT, model))
						.header("x-goog-api-key", apiKey)
						.contentType(MediaType.APPLICATION_JSON)
						.body(body)
						.retrieve()
						.body(Map.class);

				return parse(extractText(response));

			} catch (RestClientResponseException e) {

				int status = e.getStatusCode().value();  
				String responseBody = e.getResponseBodyAsString();

				lastError = new IllegalStateException(
						"Gemini API エラー status=" + status
								+ " body=" + responseBody,
						e);

				// 429 には2種類ある。
				//   1分あたりの制限 → 少し待てば直る
				//   1日あたりの制限 → 今日はもう直らない（本文に PerDay と書かれている）
				boolean dailyLimit = responseBody.contains("PerDay");

				// 5xx と、1分あたりの429だけ再試行する。
				// 403（キー不正）や 404（モデル名不正）は何度試しても直らない
				boolean retryable = (status == 429 && !dailyLimit) || status >= 500;
				if (!retryable) {
					throw lastError;
				}

				System.out.println("### Gemini 再試行 " + attempt + "回目 (status=" + status + ")");
				sleep(attempt);
			}
		}

		throw lastError;
	}

	// 再試行の前に少し待つ。回を追うごとに長くする（1秒 → 2秒 → 3秒）
	private void sleep(int attempt) {
		try {
			Thread.sleep(attempt * 1000L);
		} catch (InterruptedException e) {
			// 待っている間に中断された。割り込みの印を戻して終わる
			Thread.currentThread().interrupt();
		}
	}

	// AI に「この形で返せ」と伝えるための型定義。
	// これがあると、項目名の揺れや型違いが起きない
	private Map<String,Object> schema(){
		
		Map<String,Object>card=Map.of(
				
				"type","OBJECT",
				"properties",Map.of(
						"title",             Map.of("type", "STRING"),
						"category",          Map.of("type", "STRING"),
						"granularity",       Map.of("type", "STRING",
						                            "enum", List.of("FIVE_MIN","TODAY","GROUP")),
						"difficulty",        Map.of("type", "STRING",
						                            "enum", List.of("LOW","MID","HIGH")),
						"estimatedMinutes",  Map.of("type","INTEGER"),
						"reason",            Map.of("type","STRING"),
						"detail",            Map.of("type","STRING",
	                    "description", "手順を3行、最後の1行は必ず「終わり：」で始める。改行(\\n)区切り")

				),
				// 全部必須にする。1つでも欠けたら AI 側でやり直してくれる
				"required",List.of("title","category","granularity",
						"difficulty","estimatedMinutes","reason","detail")
				
				);
		return Map.of(
				"type", "OBJECT",
				"properties", Map.of(
					"cards", Map.of("type", "ARRAY", "items", card)
				),
				"required", List.of("cards")
			);
	
	}

	// Gemini の応答から、本文の文字列だけを取り出す
	@SuppressWarnings("unchecked")
	private String extractText(Map<?, ?> response) {

		if (response == null) {
			throw new IllegalStateException("Gemini の応答が空です");
		}

		List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");

		if (candidates == null || candidates.isEmpty()) {
			throw new IllegalStateException("candidates がありません: " + response);

		}

		Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
		List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

		return String.valueOf(parts.get(0).get("text"));

	}

	// JSON の文字列を ProposalCard のリストに変換する
	private List<ProposalCard> parse(String json) {

		List<ProposalCard> cards = new ArrayList<>();

		try {
			// JSON を木構造として読み込む
			JsonNode root = mapper.readTree(json);
			JsonNode array = root.get("cards");

			if (array == null || !array.isArray()) {
				throw new IllegalStateException("cards が配列ではありません: " + json);
			}

			// 3枚を超えて返ってきても、先頭3枚だけ使う
			int limit = Math.min(array.size(), 3);

			for (int i = 0; i < limit; i++) {
				JsonNode node = array.get(i);

				ProposalCard card = new ProposalCard();
				card.setTitle(node.get("title").asString());
				card.setCategory(node.get("category").asString());
				card.setGranularity(Granularity.valueOf(node.get("granularity").asString()));
				card.setDifficulty(Difficulty.valueOf(node.get("difficulty").asString()));
				card.setEstimatedMinutes(node.get("estimatedMinutes").asInt());
				card.setReason(node.get("reason").asString());
				card.setDetail(node.get("detail").asString());
				
				cards.add(card);

			}

		} catch (Exception e) {
			throw new IllegalStateException("AI応答の解析に失敗しました: " + json, e);

		} 

		if (cards.isEmpty()) {
			throw new IllegalStateException("カードが1枚もありません: " + json);

		}
		return cards;
	}

}
