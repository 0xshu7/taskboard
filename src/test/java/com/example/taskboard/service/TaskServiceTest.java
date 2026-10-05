package com.example.taskboard.service;

// ===== インポート =====
// 以下は JUnit の部品。
// BeforeEach  … それぞれのテストの前に動かす印
// DisplayName … テスト結果に出る名前を日本語で付ける
// Test        … テストの印
//
// 以下は Spring Boot / Spring の部品。
// DataJpaTest       … データベースまわりだけを起動する
// TestConfiguration … このテストの中だけで使う部品の作り方を書く場所
// Bean              … Spring に管理させる部品
// Import            … 上の設定を読み込ませる
// Autowired         … Spring が用意した部品を受け取る
// ActiveProfiles    … どの設定を読むか指定する
//
// 以下はこのアプリの部品。
// Task / TaskStatus … タスク1件と、その状態
// NotFoundException … 無いか、他人のものだったときに投げる例外
// TaskRepository    … タスクの出し入れ

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.exception.NotFoundException;
import com.example.taskboard.repository.TaskRepository;

/**
 * TaskService のテスト。他人のタスクに手が届かないことを確かめる。
 *
 * 【なぜここを選んだか】
 *
 * 一覧は user_id で絞っているので、他人のタスクは画面に出ない。
 * だが URL の番号を書き換えれば、出ていないものにも手が届く。
 * その入口をふさいでいるのが TaskService.findById の1か所だけで、
 * 1件を扱うすべての処理がそこを通る作りにしている。
 *
 * 作りに頼っている部分なので、効いていることを形に残しておく。
 * 目で見る確認では、アカウントを2つ用意して番号を打ち込む手間がかかる。
 * テストなら、利用者を2人に見立てて一瞬で確かめられる。
 *
 * 【Spring をどこまで動かすか】
 *
 * @DataJpaTest はデータベースまわりだけを起動する。
 * TaskService は @Service が付いているが、この起動の対象には入らないので、
 * 下の Config で作り方を書いて読み込ませている。
 *
 * 【ログインしている人をどう用意するか】
 *
 * TaskService は「いまの利用者は誰か」を UserService に聞く。
 * UserService は Googleログインの情報を見るので、そのままでは動かせない。
 *
 * そこで、ID を決め打ちで返すものを差し替えて渡している。
 * ログインの仕組みを通らずに「いま誰か」を決められる。
 *
 * 差し替えができるのは、TaskService が UserService を
 * 自分で作らず、外から受け取る形にしているため。
 *
 * 【どのデータベースを使うか】
 *
 * test プロファイルのメモリ上のH2。テストが終われば消える。
 * 本番につなぐと、試すために入れたタスクが残る。
 */
@DataJpaTest
// 置き換えを切って、application-test.yml の H2 をそのまま使う。
// 既定では @DataJpaTest が別の H2 に差し替えてしまい、
// MODE=MySQL（本番と同じ文法で動かす指定）が外れる
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TaskServiceTest.Config.class)
class TaskServiceTest {

	// ===== フィールド =====

	/** 試す相手 */
	@Autowired
	private TaskService taskService;

	/** 下ごしらえでタスクを直接入れる係 */
	@Autowired
	private TaskRepository taskRepository;

	/** ログインしていることにする利用者 */
	private static final Long ME = 1L;

	/** 別の利用者。この人のタスクには触れないはず */
	private static final Long OTHER = 2L;

	/** 自分のタスク */
	private Task mine;

	/** 他人のタスク */
	private Task theirs;

	// ===== 下ごしらえ =====

	/**
	 * それぞれのテストの前に、2人ぶんのタスクを1件ずつ入れる。
	 *
	 * 入れた行はテストが終わるたびに取り消されるので、
	 * 次のテストは空の状態から始まる。
	 */
	@BeforeEach
	void setUp() {
		mine = save(ME, "自分のタスク");
		theirs = save(OTHER, "他人のタスク");
	}

	// ===== テスト =====

	@Test
	@DisplayName("自分のタスクは取り出せる")
	void 自分のタスクは取り出せる() {

		Task found = taskService.findById(mine.getId());

		assertEquals("自分のタスク", found.getTitle());
	}

	@Test
	@DisplayName("他人のタスクのIDを渡すと、見つからない扱いになる")
	void 他人のタスクは取り出せない() {

		assertThrows(NotFoundException.class,
				() -> taskService.findById(theirs.getId()),
				"他人のタスクが取り出せてしまった");
	}

	@Test
	@DisplayName("存在しないIDでも、他人のときと同じ例外になる")
	void 無いIDも同じ例外() {

		// 返す例外を分けると、その番号のタスクがあることを
		// 相手に教えることになる。どちらも同じ扱いにしている
		assertThrows(NotFoundException.class,
				() -> taskService.findById(999_999L),
				"無いIDで別の結果になった");
	}

	@Test
	@DisplayName("他人のタスクは削除できない")
	void 他人のタスクは削除できない() {

		assertThrows(NotFoundException.class,
				() -> taskService.delete(theirs.getId()),
				"他人のタスクが消せてしまった");

		assertTrue(taskRepository.findById(theirs.getId()).isPresent(),
				"他人のタスクが消えている");
	}

	@Test
	@DisplayName("他人のタスクは書き換えられない")
	void 他人のタスクは書き換えられない() {

		assertThrows(NotFoundException.class,
				() -> taskService.update(theirs.getId(), "書き換えた", null, null, null),
				"他人のタスクが書き換えられてしまった");
	}

	// ===== 補助（テストの中だけで使うもの） =====

	/** タスクを1件、直接入れる */
	private Task save(Long userId, String title) {

		Task task = new Task();
		task.setUserId(userId);
		task.setTitle(title);
		task.setStatus(TaskStatus.TODO);

		return taskRepository.save(task);
	}

	// ===== このテストの中だけで使う部品 =====

	/**
	 * TaskService の作り方。
	 *
	 * ふだんは Spring が自動で組み立てるが、@DataJpaTest では
	 * サービスの層を起動しないので、ここで作って渡す。
	 */
	@TestConfiguration
	static class Config {

		@Bean
		TaskService taskService(TaskRepository taskRepository) {
			return new TaskService(taskRepository, new FixedUserService());
		}
	}

	/**
	 * ログインの仕組みを通らずに、利用者のIDを決め打ちで返すもの。
	 *
	 * 本物の UserService は Googleから受け取った情報を見るので、
	 * テストからは動かせない。IDを返す部分だけ差し替えている。
	 *
	 * 親のコンストラクタに null を渡しているのは、
	 * 差し替えた後は UserRepository を使わないため。
	 */
	static class FixedUserService extends UserService {

		FixedUserService() {
			super(null);
		}

		@Override
		public Long currentId() {
			return ME;
		}
	}
}
