package com.example.taskboard.ai;

// ===== インポート =====
// java.util.ArrayList … リストの実装
// java.util.List      … リストの型
// java.util.Map       … 「見出し → 値」の組。送るJSONを組み立てるのに使う
//
// 以下は Spring の部品。
// Value                 … application.yml の値をフィールドに読み込む
// ConditionalOnProperty … 設定の値によって、このクラスを使うかどうかを決める
// MediaType             … 送るデータの種類（application/json）
// Component             … Spring に管理させるクラスです、という印
// RestClient            … 外部のサービスへ通信する道具
// RestClientResponseException … 相手がエラーを返したときに投げられる例外
//
// 以下はこのアプリの部品。
// Difficulty   … 難易度
// Granularity  … タスクの粒度
// ProposalCard … 提案カード1枚
//
// 以下は Jackson（JSONを扱う道具）の部品。
// JsonNode     … JSONを木のかたちで読んだもの
// ObjectMapper … JSONと Java を行き来させる道具
//
// Spring Boot 4 から Jackson 3 になり、パッケージが変わった。
// com.fasterxml.jackson ではなく tools.jackson から始まる。
// 古い記事は com.fasterxml で書かれているので注意する。

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

/**
 * 実際に Gemini を呼んで3案を作るクラス。
 *
 * ai.mock=false のときだけ使われる。
 *
 * このアプリで唯一、外のサービスと通信する場所になる。
 * 外に出る以上、うまくいかないことは必ず起きる。
 * 混み合って断られる、1日の上限に達する、応答の形が崩れる。
 * そのため、失敗の扱いに多くの行数を割いている。
 *
 * 処理は4つに分かれている。
 * 1. 送るものを組み立てる（generate の前半）
 * 2. 送って、失敗したら試し直す（generate の繰り返し）
 * 3. 応答から本文を取り出す（extractText）
 * 4. 本文をカードに変える（parse）
 *
 * 出力の形は2重に守っている。
 * プロンプトの文章で伝え、さらに responseSchema で型を固定する。
 * 文章だけだと、項目名が揺れたり型が違ったりする。
 *
 * ここで投げた例外は ProposalService が受け止め、固定3案に切り替える。
 * つまりこのクラスは、失敗したら素直に失敗してよい。
 * 無理に何かを返そうとせず、投げたほうが後始末が1か所にまとまる。
 */

// ===== アノテーション =====
// @Component … Spring に管理させるクラスです、という印。
//
// @ConditionalOnProperty … ai.mock が false のときだけ、このクラスを使う。
//   MockProposalGenerator には havingValue="true" が付いているので、
//   どちらか一方だけが選ばれる。

@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "false")
public class RealProposalGenerator implements ProposalGenerator {

	// ===== フィールド =====

	/**
	 * Gemini の呼び出し先。
	 *
	 * %s の部分にモデル名が入る。String.format で差し込む。
	 * v1beta の綴りを間違えると、本文の無い404が返る。
	 * キーやモデル名が間違っている場合は、JSONで理由が返るので区別できる。
	 */
	private static final String ENDPOINT =
			"https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

	/**
	 * 応答のばらけ具合。プロンプト設計書 1.2 の初期値。
	 *
	 * 提案は毎回少し違うものが欲しいので 0.8 にしている。
	 * 3案が似る、毎回同じものが出る、というときは 0.9〜1.0 に上げる。
	 * 逆に、指示を無視して勝手なものを作るときは下げる。
	 *
	 * 設計書に書いてあったのに、長いあいだ指定し忘れていた。
	 * 指定しないと Gemini の既定値になる。
	 */
	private static final double TEMPERATURE = 0.8;

	/** 外部へ通信する道具 */
	private final RestClient rest = RestClient.create();

	/** JSON を Java のかたちに変換する道具 */
	private final ObjectMapper mapper = new ObjectMapper();

	/** 使うモデルの名前。application.yml の ai.model を読む */
	@Value("${ai.model}")
	private String model;

	/**
	 * APIキー。application.yml の ai.api-key を読む。
	 *
	 * yml には環境変数から読む指定だけを書き、値そのものは書かない。
	 * コードにも yml にも書かないので、リポジトリに載ることがない。
	 */
	@Value("${ai.api-key}")
	private String apiKey;

	/**
	 * 何回まで試し直すか。application.yml の ai.max-attempts を読む。
	 * 末尾の :3 は「設定が無ければ3」という既定値。
	 */
	@Value("${ai.max-attempts:3}")
	private int maxAttempts;

	// ===== メソッド =====

	/**
	 * この実装が何者かを表す文字を返す。ProposalSet.source に記録される。
	 *
	 * @return "AI"
	 */
	@Override
	public String sourceName() {
		return "AI";
	}

	/**
	 * Gemini を呼んで3案を作る。
	 *
	 * 送るものの組み立て：
	 * contents         … 送る文章そのもの
	 * responseMimeType … JSONで返すよう指定する
	 * responseSchema   … その JSON の形を固定する
	 * temperature      … 応答のばらけ具合（0.8）
	 * thinkingLevel    … 考える深さ。low にすると速くなる
	 * maxOutputTokens  … 返す長さの上限
	 *
	 * thinkingLevel を low にしたのは、応答が12秒かかっていたため。
	 * 設定を1行変えただけで5秒台まで落ちた。
	 * 推測で直す前に、まず時間を測って判断している。
	 *
	 * 失敗したときの扱い：
	 * 429（回数制限）と 5xx（相手側の問題）は、時間をおけば直ることがある。
	 * ただし429には2種類あり、1日の上限に達した場合は何度試しても直らない。
	 * 本文に PerDay と書かれているかどうかで見分けている。
	 *
	 * 403（キーが違う）や 404（モデル名が違う）は、待っても直らない。
	 * すぐに投げて、呼び出す側へ渡す。
	 *
	 * @param prompt AI に送る文章
	 * @return 3枚のカード
	 */
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
						"temperature", TEMPERATURE,
						"thinkingConfig", Map.of("thinkingLevel", "low"),
						"maxOutputTokens", 800));

		// 最後に起きた失敗を覚えておく。
		// 全部だめだったとき、これを投げて理由を伝える。
		RuntimeException lastError = null;

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

				// 5xx と、1分あたりの429だけ試し直す。
				// 403（キー不正）や 404（モデル名不正）は何度試しても直らない。
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

	/**
	 * 試し直す前に少し待つ。回を追うごとに長くする（1秒 → 2秒 → 3秒）。
	 *
	 * すぐに投げ直すと、混んでいる相手をさらに叩くことになる。
	 * 回を追うごとに間隔を空けるのは、外部サービスを呼ぶときの基本になっている。
	 *
	 * 待っている間に中断された場合は、割り込みの印を戻してから終わる。
	 * 印を戻さないと、呼び出した側が「中断された」ことに気づけなくなる。
	 *
	 * @param attempt 何回目の試行か
	 */
	private void sleep(int attempt) {
		try {
			Thread.sleep(attempt * 1000L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * AI に「この形で返せ」と伝えるための型定義を組み立てる。
	 *
	 * これがあると、項目名の揺れや型の違いが起きない。
	 * 文章で「JSONで返して」と頼むだけでは、
	 * estimatedMinutes が文字で返ってきたり、項目が欠けたりする。
	 *
	 * 型名は大文字で書く。STRING であって String ではない。
	 *
	 * enum を指定した項目は、その値しか返せなくなる。
	 * granularity と difficulty は、そのまま列挙型に変換するので、
	 * 想定外の値が来ると変換で失敗する。ここで塞いでおく。
	 *
	 * required に全部並べているので、1つでも欠けると AI 側でやり直してくれる。
	 *
	 * Map.of は10組までしか書けない。
	 * いま7組なのでまだ余裕があるが、これ以上増やすときは
	 * Map.ofEntries に変える必要がある。
	 *
	 * 【description はプロンプトより強い】
	 *
	 * detail に付けた description が「終わり：」のままだったとき、
	 * プロンプト側を「おわりの目安:」に変えても、返ってくるのは「終わり：」だった。
	 * 項目のすぐ隣に書いてある説明のほうが、離れたところにある文章より効く。
	 *
	 * 逆に言えば、プロンプトで言うことを聞かない項目があるときは、
	 * ここに一行書くのが早い。ただし、プロンプトと食い違うことを書くと
	 * 今回のように、直したはずのものが直らない。両方そろえること。
	 *
	 * @return 型定義
	 */
	private Map<String, Object> schema() {

		Map<String, Object> card = Map.of(

				"type", "OBJECT",
				"properties", Map.of(
						"title",            Map.of("type", "STRING"),
						"category",         Map.of("type", "STRING"),
						"granularity",      Map.of("type", "STRING",
						                           "enum", List.of("FIVE_MIN", "TODAY", "GROUP")),
						"difficulty",       Map.of("type", "STRING",
						                           "enum", List.of("LOW", "MID", "HIGH")),
						"estimatedMinutes", Map.of("type", "INTEGER"),
						"reason",           Map.of("type", "STRING"),
						"detail",           Map.of("type", "STRING",
						                           "description",
						                           "「✓」で始まる手順を3行、最後の1行は「おわりの目安: 」で始める（✓は付けない）。改行(\\n)区切り")),

				// 全部必須にする。1つでも欠けたら AI 側でやり直してくれる
				"required", List.of("title", "category", "granularity",
						"difficulty", "estimatedMinutes", "reason", "detail"));

		return Map.of(
				"type", "OBJECT",
				"properties", Map.of(
						"cards", Map.of("type", "ARRAY", "items", card)),
				"required", List.of("cards"));
	}

	/**
	 * Gemini の応答から、本文の文字列だけを取り出す。
	 *
	 * 応答は入れ子になっている。
	 * candidates → 0番目 → content → parts → 0番目 → text
	 *
	 * 段ごとに確かめてから次へ進んでいる。
	 * 途中が空のまま進むと、どこで失敗したのか分からない形で落ちる。
	 *
	 * @SuppressWarnings("unchecked") は、型の警告を消す指定。
	 * Map から取り出したものを List に変換するとき、
	 * Java は中身の型まで確かめられないので警告を出す。
	 * ここでは応答の形が決まっているため、承知のうえで消している。
	 *
	 * @param response Gemini からの応答
	 * @return 本文の文字列（JSONが入っている）
	 */
	@SuppressWarnings("unchecked")
	private String extractText(Map<?, ?> response) {

		if (response == null) {
			throw new IllegalStateException("Gemini の応答が空です");
		}

		List<Map<String, Object>> candidates =
				(List<Map<String, Object>>) response.get("candidates");

		if (candidates == null || candidates.isEmpty()) {
			throw new IllegalStateException("candidates がありません: " + response);
		}

		Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
		List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

		return String.valueOf(parts.get(0).get("text"));
	}

	/**
	 * JSON の文字列を ProposalCard のリストに変換する。
	 *
	 * readTree は、JSON を木のかたちで読み込む。
	 * 決まったクラスに当てはめずに、必要なところだけ取り出せる。
	 *
	 * asString は Jackson 3 での書き方。
	 * Jackson 2 の asText は使われなくなった。
	 *
	 * 3枚を超えて返ってきても、先頭3枚だけ使う。
	 * 画面は3枚しか置けないので、多い分は捨てる。
	 *
	 * 失敗したときは、受け取った JSON をそのまま例外に添えている。
	 * 何が返ってきたのか分からないと、原因を追えないため。
	 *
	 * @param json Gemini が返した JSON の文字列
	 * @return 3枚のカード
	 */
	private List<ProposalCard> parse(String json) {

		List<ProposalCard> cards = new ArrayList<>();

		try {
			JsonNode root = mapper.readTree(json);
			JsonNode array = root.get("cards");

			if (array == null || !array.isArray()) {
				throw new IllegalStateException("cards が配列ではありません: " + json);
			}

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