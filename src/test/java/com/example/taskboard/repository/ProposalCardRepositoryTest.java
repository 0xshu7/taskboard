package com.example.taskboard.repository;

// ===== インポート =====
// 以下は JUnit の部品。
// DisplayName … テスト結果に出る名前を日本語で付ける
// Test        … テストの印
//
// 以下は Spring Boot の部品。
// DataJpaTest   … データベースまわりだけを起動する。画面も通知もAIも動かさない
// Autowired     … Spring が用意した部品を受け取る
// ActiveProfiles … どの設定を読むか指定する。test を指定するとメモリ上のH2になる
//
// 以下はこのアプリの部品。
// ProposalCard / ProposalSet / Task … 数える対象の3つのテーブル
// TaskStatus                        … 完了したかどうか

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;

/**
 * ProposalCardRepository.countByCategory のテスト。
 *
 * 【なぜこのメソッドを選んだか】
 *
 * このアプリで SQL を自分で書いているのは、ここだけ。
 * ほかの検索はメソッド名から Spring が組み立てるので、書き間違える余地がない。
 * 自分で書いた1つは、思ったとおりに数えているかを確かめる値がある。
 *
 * 数え方を間違えても画面は普通に動く。
 * 提案の分野が少し偏るだけなので、見ていても気づけない。
 * だから目で見る確認ではなく、テストで押さえる。
 *
 * 【ほかのテストとの違い】
 *
 * SituationJudge のテストは new して呼ぶだけで、Spring も動かない。
 * こちらは3つのテーブルをまたぐ SQL なので、本物のデータベースが要る。
 * 表を作り、行を入れ、数えさせて、返ってきた数を見る。
 *
 * @DataJpaTest はデータベースまわりだけを起動する。
 * 画面もログインもAIも立ち上がらないので、Googleのキーが無くても走る。
 *
 * 【どのデータベースを使うか】
 *
 * test プロファイル（src/test/resources/application-test.yml）の
 * メモリ上のH2。テストが終われば消える。
 *
 * 本番のMySQLにつなぐと、試すために入れた行が残る。
 * dev のH2ファイルでも、画面で見ているデータに混ざる。
 * どちらも、あとから「この行は何だったか」が分からなくなる。
 *
 * 【後片付けをしていない理由】
 *
 * @DataJpaTest は1つのテストが終わるたびに、その中の保存を取り消す。
 * 次のテストは空の状態から始まるので、消す処理を書かなくてよい。
 */
@DataJpaTest
// 置き換えを切って、application-test.yml の H2 をそのまま使う。
// 既定では @DataJpaTest が別の H2 に差し替えてしまい、
// MODE=MySQL（本番と同じ文法で動かす指定）が外れる
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProposalCardRepositoryTest {

	// ===== フィールド =====

	/** 試す相手 */
	@Autowired
	private ProposalCardRepository cardRepository;

	/** セットを入れる係。カードは単体では置けないので先にセットが要る */
	@Autowired
	private ProposalSetRepository setRepository;

	/** タスクを入れる係。終わったかどうかはタスク側が持っている */
	@Autowired
	private TaskRepository taskRepository;

	/** 数える対象の利用者 */
	private static final Long ME = 1L;

	/** 数えてはいけない、別の利用者 */
	private static final Long OTHER = 2L;

	/** 数えはじめる時刻。ふだんは3問に答えた時刻が入る */
	private static final LocalDateTime SINCE = LocalDateTime.of(2026, 10, 1, 0, 0);

	// ===== テスト =====

	@Test
	@DisplayName("選ばれなかったカードも、出した数には入る")
	void 選ばれなかったカードも出した数に入る() {

		ProposalSet set = saveSet(ME, SINCE.plusHours(1));

		// 1枚は選ばれて完了、残り2枚は選ばれていない
		saveCard(set, 1, "べんきょう", saveTask(ME, TaskStatus.DONE));
		saveCard(set, 2, "べんきょう", null);
		saveCard(set, 3, "べんきょう", null);

		Map<String, long[]> result = count();

		assertEquals(3, result.get("べんきょう")[0], "出した数が3になっていない");
		assertEquals(1, result.get("べんきょう")[1], "終わった数が1になっていない");
	}

	@Test
	@DisplayName("選ばれても終わっていなければ、終わった数には入らない")
	void 終わっていないものは終わった数に入らない() {

		ProposalSet set = saveSet(ME, SINCE.plusHours(1));

		// 選ばれてタスクになったが、まだ進行中
		saveCard(set, 1, "せいかつ", saveTask(ME, TaskStatus.DOING));
		saveCard(set, 2, "せいかつ", saveTask(ME, TaskStatus.DONE));

		Map<String, long[]> result = count();

		assertEquals(2, result.get("せいかつ")[0], "出した数が2になっていない");
		assertEquals(1, result.get("せいかつ")[1], "進行中のものが終わった数に入っている");
	}

	@Test
	@DisplayName("数えはじめる時刻より前に出したものは、数えない")
	void 起点より前は数えない() {

		// 3問に答え直すと起点が動く。それより前の記録は評価に入れない
		ProposalSet before = saveSet(ME, SINCE.minusDays(1));
		saveCard(before, 1, "リラックス", saveTask(ME, TaskStatus.DONE));

		ProposalSet after = saveSet(ME, SINCE.plusHours(1));
		saveCard(after, 1, "リラックス", null);

		Map<String, long[]> result = count();

		assertEquals(1, result.get("リラックス")[0], "起点より前のカードが数に入っている");
		assertEquals(0, result.get("リラックス")[1], "起点より前の完了が数に入っている");
	}

	@Test
	@DisplayName("ほかの利用者のカードは、数えない")
	void 他人のカードは数えない() {

		ProposalSet mine = saveSet(ME, SINCE.plusHours(1));
		saveCard(mine, 1, "べんきょう", null);

		ProposalSet theirs = saveSet(OTHER, SINCE.plusHours(1));
		saveCard(theirs, 1, "べんきょう", saveTask(OTHER, TaskStatus.DONE));
		saveCard(theirs, 2, "せいかつ", null);

		Map<String, long[]> result = count();

		assertEquals(1, result.get("べんきょう")[0], "他人のカードが数に入っている");
		assertEquals(0, result.get("べんきょう")[1], "他人の完了が数に入っている");
		assertNull(result.get("せいかつ"), "他人しか出していない分野が返ってきた");
	}

	// ===== 補助（テストの中だけで使うもの） =====

	/**
	 * countByCategory を呼び、「分野 → [出した数, 終わった数]」の形にして返す。
	 *
	 * 返ってくるのは Object[] の並びで、そのままでは読みにくい。
	 * 数の取り出し方を1か所にまとめておくと、各テストが短くなる。
	 *
	 * SUM は1件も無いと null を返すので、そこだけ 0 に置き換えている。
	 */
	private Map<String, long[]> count() {

		Map<String, long[]> map = new HashMap<>();

		for (Object[] row : cardRepository.countByCategory(ME, SINCE)) {

			long shown = ((Number) row[1]).longValue();
			long done = row[2] == null ? 0L : ((Number) row[2]).longValue();

			map.put((String) row[0], new long[] { shown, done });
		}
		return map;
	}

	/** 提案セットを1つ入れる。requested_at が起点との比較に使われる */
	private ProposalSet saveSet(Long userId, LocalDateTime requestedAt) {

		ProposalSet set = new ProposalSet();
		set.setUserId(userId);
		set.setRequestedAt(requestedAt);

		return setRepository.save(set);
	}

	/**
	 * カードを1枚入れる。
	 *
	 * @param resultTask 選ばれてできたタスク。選ばれていないときは null
	 */
	private ProposalCard saveCard(ProposalSet set, int position, String category, Task resultTask) {

		ProposalCard card = new ProposalCard();
		card.setProposalSetId(set.getId());
		card.setPosition(position);
		card.setTitle("テスト用のカード" + position);
		card.setCategory(category);

		if (resultTask != null) {
			card.setChosen(true);
			card.setResultTaskId(resultTask.getId());
		}
		return cardRepository.save(card);
	}

	/** タスクを1件入れる。完了かどうかが「終わった数」に効く */
	private Task saveTask(Long userId, TaskStatus status) {

		Task task = new Task();
		task.setUserId(userId);
		task.setTitle("テスト用のタスク");
		task.setStatus(status);

		return taskRepository.save(task);
	}
}
