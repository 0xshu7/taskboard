package com.example.taskboard.service;

// ===== インポート =====
// java.nio.charset.StandardCharsets … 文字とバイトの変換のしかた。UTF-8 を指定する
// java.time.LocalDate               … 日付だけ。締切の比較に使う
// java.util.List                    … リストの型
//
// 以下は Spring の部品。
// Scheduled     … 決まった時刻にメソッドを自動で動かす
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下は外部のライブラリ。
// Notification … 送る内容1件ぶん（届け先＋本文）
// PushService  … 実際に送る係。PushConfig で作ったものを受け取る
//
// 以下はこのアプリの部品。
// PushSubscription           … 通知の届け先1件
// Task                       … タスク1件
// TaskStatus                 … タスクの状態
// User                       … 利用者1人
// PushSubscriptionRepository … 届け先の出し入れ
// TaskRepository             … タスクの出し入れ
// UserRepository             … 利用者の出し入れ

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.PushSubscription;
import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.domain.User;
import com.example.taskboard.repository.PushSubscriptionRepository;
import com.example.taskboard.repository.TaskRepository;
import com.example.taskboard.repository.UserRepository;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

/**
 * 毎朝の声かけを、ブラウザの通知で届ける。
 *
 * 本文に並べるのは「締切が今日か明日」かつ「未完了」のタスク。
 *
 * 【締切が無い日も送る】
 *
 * ここは前と考えを変えたところ。
 * 以前は締切を知らせることが目的だったので、締切が無ければ何も送らなかった。
 * いまの目的は毎朝声をかけることなので、締切の有無で送るかどうかを決めない。
 * 締切が無い日は「直近の締切はありません」と書いて、声かけだけを送る。
 *
 * 【メールをやめた理由】
 *
 * メールアドレスを持たなくて済むこと。
 * 保存しているのは「どの端末のどのブラウザか」を表す文字列だけで、
 * それ自体では誰かを特定できない。
 *
 * 本文は届け先ごとの鍵で暗号化して送るので、
 * 途中を通る提供元の窓口（GoogleやMozilla）からは中身が読めない。
 *
 * 【通知の入切を利用者側に持たせていない理由】
 *
 * 届け先が登録されていれば送る、無ければ送らない。それだけ。
 * 「通知する設定なのに届け先が無い」という食い違いが、そもそも起きない。
 * 状態を2か所に持たないための判断。
 *
 * 【入口は2つ】
 *
 * checkAndNotify … 時計から呼ばれる。毎朝8時に、全員に対して送る。
 *                  ブラウザからの要求ではないので、ログインしている人がいない。
 *                  だから「いまの利用者」ではなく、全員を取り出して回す。
 *
 * sendNow        … 画面の「通知を試す」から呼ばれる。ログインしている本人にだけ送る。
 *                  時刻は見ない。届くかどうかを確かめるためのもの。
 *                  朝と同じ本文が出るので、実際の見え方をその場で確認できる。
 */
@Service
public class NotificationService {

	// ===== フィールド =====

	/** 本文に並べるタスクの上限。3件目からは「など」でまとめる */
	private static final int MAX_LINES = 2;

	/** タスク名をこの文字数で切る。超えた分は「…」にする */
	private static final int TITLE_LIMIT = 15;

	/** 通知の見出し。締切の件数によらず同じ */
	private static final String GREETING = "おはようございます";

	/** 本文の最後に必ず置く声かけ */
	private static final String CHEER = "今日もコツコツいきましょう！";

	/** タスクの出し入れ */
	private final TaskRepository taskRepository;

	/** 利用者の出し入れ。自動送信で全員を取り出すのに使う */
	private final UserRepository userRepository;

	/** 通知の届け先の出し入れ */
	private final PushSubscriptionRepository subscriptionRepository;

	/** いまログインしている利用者を教えてくれる係。試し送りで使う */
	private final UserService userService;

	/** 通知を送る係。PushConfig が作ったものを受け取る */
	private final PushService pushService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public NotificationService(TaskRepository taskRepository,
			UserRepository userRepository,
			PushSubscriptionRepository subscriptionRepository,
			UserService userService,
			PushService pushService) {

		this.taskRepository = taskRepository;
		this.userRepository = userRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.userService = userService;
		this.pushService = pushService;
	}

	// ===== メソッド（入口） =====

	/**
	 * 毎朝8時に、全員へ声かけを送る。
	 *
	 * cron は左から「秒 分 時 日 月 曜日」の6つ。
	 * "0 0 8 * * *" は「毎日8時0分0秒」という意味。* は「すべて」を表す。
	 *
	 * 時刻を選べるようにはしていない。
	 * 設定項目が増えるほど、使い始めるまでに決めることが増える。
	 * このアプリは決めることを減らすためのものなので、時刻は固定にした。
	 *
	 * 届け先を登録していない人には、何も送られない。
	 * send の中で届け先が0件になり、繰り返しが1回も回らないため。
	 *
	 * 利用者が増えると、毎朝全員を読み出すことになる。
	 * 届け先のある人だけを取り出す検索を足せば軽くなるが、
	 * いまの人数では違いが出ないので、そのときに直す。
	 */
	@Scheduled(cron = "0 0 8 * * *")
	@Transactional
	public void checkAndNotify() {
		for (User user : userRepository.findAll()) {
			sendFor(user);
		}
	}

	/**
	 * いまログインしている人に、朝と同じ通知を送る。
	 *
	 * 時刻は見ない。押されたら必ず処理する。
	 * 通知が届くかどうかと、本文がどう見えるかを、その場で確かめるためのもの。
	 *
	 * @Transactional を付けているのは、使えなくなった届け先を消す処理があるため。
	 * delete で始まるリポジトリのメソッドは、これが無いと実行時に失敗する。
	 */
	@Transactional
	public void sendNow() {
		sendFor(userService.current());
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * ある利用者に、朝の声かけを1件の通知にまとめて送る。
	 *
	 * 締切が0件でも送る。
	 * 声をかけること自体が目的なので、並べるものが無いことは止める理由にならない。
	 *
	 * @param user 送る相手
	 */
	private void sendFor(User user) {

		LocalDate today = LocalDate.now();
		LocalDate tomorrow = today.plusDays(1);

		// 締切が今日か明日で、まだ完了していないタスクを取る
		List<Task> targets = taskRepository
				.findByUserIdAndStatusNotAndDueDateBetweenOrderByDueDateAsc(
						user.getId(), TaskStatus.DONE, today, tomorrow);

		send(user, GREETING, buildBody(targets, today));
	}

	/**
	 * 登録されている届け先すべてに送る。
	 *
	 * 朝の声かけと、自動完了の通知の両方から呼ばれる。
	 * 送る仕組みはここ1か所にまとめてある。
	 */
	public void send(User user, String title, String body) {
		// JSONを組み立てる。sw.js がこれを受け取って画面に出す。
		// 改行や引用符をそのまま入れると JSON として読めなくなるので、escape で直す
		String payload = "{\"title\":\"" + escape(title) + "\",\"body\":\"" + escape(body) + "\"}";

		for (PushSubscription subscription : subscriptionRepository.findByUserId(user.getId())) {

			try {
				// 本文はバイトの並びで渡す。
				// 暗号化して送るので、ライブラリは中身を文字として扱わない。
				//
				// 応答は var で受ける。「右側の型をそのまま使う」という書き方。
				// HttpResponse という名前のクラスは Java 標準にもあり、
				// import を書くと別のほうを取り込んでしまうため、型名を書かずに済ませている
				var response = pushService.send(new Notification(
						subscription.getEndpoint(),
						subscription.getP256dh(),
						subscription.getAuth(),
						payload.getBytes(StandardCharsets.UTF_8)));

				int status = response.getStatusLine().getStatusCode();
				System.out.println("### 通知の送信 status=" + status);

				// 201 が成功。404 と 410 は「その届け先はもう無い」という意味。
				// 相手は例外を投げてくれないので、応答の番号を自分で見ないと気づけない。
				// 残しておくと、毎回失敗する送信を繰り返すことになる
				if (status == 404 || status == 410) {
					System.out.println("### 届け先が無効になっていたので削除します");
					subscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
				}

			} catch (Exception e) {
				// 送ること自体に失敗した。次の届け先へ進む。
				// ネットワークが一時的に切れただけのこともあるので、届け先は消さない。
				// 届け先そのものはログに出さない。長い文字列で、端末を指すものなので
				System.out.println("### 通知の送信に失敗: " + e.getMessage());
			}
		}
	}

	/**
	 * JSON の文字列に入れられる形に直す。
	 *
	 * 3つを置き換える。順番が大事で、\ を最初にする。
	 * あとにすると、他の置き換えで足した \ まで二重に置き換えてしまう。
	 *
	 *   \    … \\ にする
	 *   "    … \" にする（ここで文字列が終わったと誤解されるため）
	 *   改行 … \n という2文字にする（JSON の中に生の改行は書けない）
	 *
	 * ここでまとめて処理するので、本文を組み立てるほうでは記号を気にしなくてよい。
	 *
	 * @param text 元の文字
	 * @return JSON に入れられる形
	 */
	private String escape(String text) {
		return text
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n");
	}

	/**
	 * 通知の本文を作る。
	 *
	 * 形はこう決めた。全体で4行、最大67字。
	 * Windows のトーストは本文が約70字・5行で切れるので、その中に収まる。
	 *
	 * 　直近の締切：3件
	 * 　・（今日）レポートの下書きを書く
	 * 　・（明日）部屋の片づけをする など
	 * 　今日もコツコツいきましょう！
	 *
	 * 「（今日）（明日）」を残したのは、締切順に並べても、
	 * 先頭が今日なのか明日なのかが本文だけでは分からないため。
	 * 今日出すものと明日でいいものは、朝いちばんに見分けたい情報になる。
	 *
	 * 「ほか1件」の行は作らない。
	 * 1行目に件数が出ているので、2件しか並んでいなければ残りがあることは読める。
	 * 1行使うと、機種によって最後の声かけが切れてしまう。
	 *
	 * 「など」は3件以上のときだけ付ける。
	 * 全部並んでいるのに付けると、まだ何かあるように読めてしまう。
	 *
	 * 急かす表現は入れない。締切が近いことは、書かなくても本人が一番分かっている。
	 *
	 * @param tasks 締切が今日か明日の未完了タスク。0件のこともある
	 * @param today 今日の日付。今日締切か明日締切かの判定に使う
	 * @return 本文
	 */
	private String buildBody(List<Task> tasks, LocalDate today) {

		// 文字列を何度も足すときは StringBuilder を使う。
		// String の + は、足すたびに新しい文字列を作るため、
		// 繰り返しの中で使うと無駄が積み重なる
		StringBuilder body = new StringBuilder();

		// 締切が無い日。件数を書かずに、無いことだけ伝える
		if (tasks.isEmpty()) {
			body.append("直近の締切はありません\n");
			body.append(CHEER);
			return body.toString();
		}

		body.append("直近の締切：").append(tasks.size()).append("件\n");

		// 並べる件数。2件までで、それより少なければその数だけ
		int shown = Math.min(tasks.size(), MAX_LINES);

		for (int i = 0; i < shown; i++) {

			Task task = tasks.get(i);

			// 条件 ? A : B は「条件が成り立てば A、そうでなければ B」という書き方
			String when = task.getDueDate().equals(today) ? "今日" : "明日";

			body.append("・（").append(when).append("）").append(cut(task.getTitle()));

			// 並べきれていないときだけ、最後の行に付ける
			if (i == shown - 1 && tasks.size() > MAX_LINES) {
				body.append(" など");
			}

			body.append("\n");
		}

		body.append(CHEER);

		return body.toString();
	}

	/**
	 * タスク名が長いときに切る。
	 *
	 * 通知は横幅が決まっているので、長い名前をそのまま出すと
	 * 2行に折り返して、2件目や声かけが押し出される。
	 *
	 * 絵文字が入っていると1文字を2つと数えることがあるが、
	 * その分だけ短く切られるだけで、表示は崩れないのでこのままにしている。
	 *
	 * @param title 元のタスク名
	 * @return 15字までに収めた名前
	 */
	private String cut(String title) {

		if (title.length() <= TITLE_LIMIT) {
			return title;
		}

		return title.substring(0, TITLE_LIMIT) + "…";
	}
}