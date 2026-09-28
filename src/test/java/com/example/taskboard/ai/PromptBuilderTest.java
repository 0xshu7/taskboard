package com.example.taskboard.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 前置きの言葉のテスト。
 *
 * v5.0 で「1件目と2件目が同じ前置きになる」という不具合を出した。
 * 分野ごとに違う言葉を返すことが、その再発を防いでいる。
 * ここが崩れても画面はエラーにならないので、テストで押さえておく。
 */
class PromptBuilderTest {

	private static final List<String> TIMES =
			List.of("朝", "昼", "夜", "深夜");

	private static final List<String> CATEGORIES =
			List.of("べんきょう", "せいかつ", "リラックス");

	@Test
	@DisplayName("同じ時間帯では、3分野の前置きがすべて違う")
	void 同じ時間帯で前置きが重ならない() {

		for (String time : TIMES) {

			String a = PromptBuilder.wordsFor(time, "べんきょう");
			String b = PromptBuilder.wordsFor(time, "せいかつ");
			String c = PromptBuilder.wordsFor(time, "リラックス");

			assertNotEquals(a, b, time + " で前置きが重なった");
			assertNotEquals(b, c, time + " で前置きが重なった");
			assertNotEquals(a, c, time + " で前置きが重なった");
		}
	}

	@Test
	@DisplayName("12通りとも、空でない言葉が返る")
	void すべての組に言葉がある() {

		for (String time : TIMES) {
			for (String category : CATEGORIES) {

				String words = PromptBuilder.wordsFor(time, category);

				org.junit.jupiter.api.Assertions.assertNotNull(words);
				org.junit.jupiter.api.Assertions.assertFalse(words.isBlank(),
						time + " × " + category + " の前置きが空");
			}
		}
	}

	@Test
	@DisplayName("知らない時間帯が来ても落ちない")
	void 想定外の時間帯() {

		// switch の default に落ちる。深夜と同じ言葉になる
		assertEquals(PromptBuilder.wordsFor("深夜", "べんきょう"),
				PromptBuilder.wordsFor("夕方", "べんきょう"));
	}
}