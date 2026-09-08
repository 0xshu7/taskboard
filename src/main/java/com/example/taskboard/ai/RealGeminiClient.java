package com.example.taskboard.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;


/**
 * 本物の Gemini API を呼ぶ実装。ai.mock=false のときだけ Bean になる。
 * 無料枠を消費するので、通常の開発中は ai.mock=true（MockGeminiClient）を使う。
 */
@Component
@ConditionalOnProperty(name="ai.mock",havingValue="false")
public class RealGeminiClient implements GeminiClient {
	
	private static final String ENDPOINT =
			"https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
	
	private final RestClient rest = RestClient.create();
	
	@Value("${ai.model}")
	private String model;
	
	@Value("${ai.api-key}")
	private String apiKey;
	
	@Override
	public String generate(String purpose, String prompt) {
		if(apiKey==null||apiKey.isBlank()) {
			throw new IllegalStateException(
					"GEMINI_API_KEY が設定されていません。実行構成 > 環境タブを確認してください。");
		}
		
		Map<String,Object>body=Map.of(
				"contents",List.of(
						Map.of("parts",List.of(Map.of("text",prompt)))
				),
				"generationConfig",Map.of(
						"responseMimeType","application/json"
				)
			);
		
		try {
			Map<?, ?>res=rest.post()
					.uri(String.format(ENDPOINT, model))
					.header("x-goog-api-key",apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);
			return extractText(res);
			
		}catch(RestClientResponseException e) {
			// 403=キー不正 / 404=モデル名不正 / 429=無料枠超過 を切り分けられるようにする
			throw new IllegalStateException(
					"Gemini API エラー purpose=" + purpose
					+ " status=" + e.getStatusCode()
					+ "body=" + e.getResponseBodyAsString(),e);
		}
		
	}
	/** candidates[0].content.parts[0].text を取り出す */
	@SuppressWarnings("unchecked")
	private String extractText(Map<?, ?>res) {
		if(res==null) {
			throw new IllegalStateException("Gemini の応答が空です");
			
		}
		List<Map<String,Object>> candidates=
		(List<Map<String,Object>>)res.get("candidates");
		if(candidates==null||candidates.isEmpty()) {
			throw new IllegalStateException("candidates がありません: " +res);
			
		}
		Map<String,Object>content=
				(Map<String,Object>)candidates.get(0).get("content");
		List<Map<String,Object>> parts=
				(List<Map<String,Object>>)content.get("parts");
		return String.valueOf(parts.get(0).get("text"));
		

	}

}
