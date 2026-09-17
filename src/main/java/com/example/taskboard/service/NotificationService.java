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
 * 締切が近いタスクを、ブラウザの通知で知らせる。
 *
 * 対象は「締切が今日か明日」かつ「未完了」のタスク。
 *
 * メールをやめてこの形にした理由は、メールアドレスを持たなくて済むこと。
 * 保存しているのは「どの端末のどのブラウザか」を表す文字列だけで、
 * それ自体では誰かを特定できない。
 *
 * 本文は届け先ごとの鍵で暗号化して送るので、
 * 途中を通る提供元の窓口（GoogleやMozilla）からは中身が読めない。
 *
 * 通知の入切は、利用者の側には持たせていない。
 * 届け先が登録されていれば送る、無ければ送らない。それだけ。
 * 「通知する設定なのに届け先が無い」という食い違いが、そもそも起きない。
 *
 * 入口は2つある。
 *
 * checkAndNotify … 時計から呼ばれる。毎朝8時に、全員に対して送る。
 *                  ブラウザからの要求ではないので、ログインしている人がいない。
 *                  だから「いまの利用者」ではなく、全員を取り出して回す。
 *
 * sendNow        … 画面のボタンから呼ばれる。ログインしている本人にだけ送る。
 *                  時刻は見ない。届くかどうかを確かめるためのもの。
 */
@Service
public class NotificationService {

	// ===== フィールド =====

	/** 本文に並べるタスクの上限。通知に表示できる行数は限られている */
	private static final int MAX_LINES = 3;

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
	 * 毎朝8時に、締切が近いタスクを知らせる。
	 *
	 * cron は左から「秒 分 時 日 月 曜日」の6つ。
	 * "0 0 8 * * *" は「毎日8時0分0秒」という意味。* は「すべて」を表す。
	 *
	 * 時刻を選べるようにはしていない。
	 * 設定項目が増えるほど、使い始めるまでに決めることが増える。
	 * このアプリは決めることを減らすためのものなので、時刻は固定にした。
	 *
	 * 届け先を登録していない人には、何も送られない。
	 * sendFor の中で届け先が0件になり、繰り返しが1回も回らないため。
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
	 * いまログインしている人に、締切が近いタスクを送る。
	 *
	 * 時刻は見ない。押されたら必ず処理する。
	 * 通知が届くかどうかを、その場で確かめるためのもの。
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
	 * ある利用者について、締切が近いタスクを1件の通知にまとめて送る。
	 *
	 * 送るものが無ければ、何もせずに終わる。
	 * 「今日は何もありません」を毎日送ると、そのうち見られなくなる。
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

		// 送るものが無ければ、通知は送らない
		if (targets.isEmpty()) {
			return;
		}

		send(user, buildTitle(targets), buildBody(targets, today));
	}

	/**
	 * 登録されている届け先すべてに送る。
	 *
	 * 1人が複数の端末で許可していることがあるので、全部に送る。
	 * 1件も登録が無ければ、繰り返しが1回も回らず、何も起きない。
	 *
	 * 1つ失敗しても他は送りたいので、繰り返しの中で受け止めている。
	 *
	 * @param user  送る相手
	 * @param title 通知の見出し
	 * @param body  通知の本文
	 */
	private void send(User user, String title, String body) {

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
	 * 通知の見出しを作る。
	 *
	 * 通知は一瞬しか表示されないので、見出しだけで状況が分かるようにする。
	 *
	 * @param tasks 送る対象のタスク
	 * @return 見出し
	 */
	private String buildTitle(List<Task> tasks) {
		return "締切が近いタスクが " + tasks.size() + " 件";
	}

	/**
	 * 通知の本文を作る。
	 *
	 * 事実だけを並べる。急かす表現は入れない。
	 * 締切が近いことは、書かなくても本人が一番分かっている。
	 *
	 * 通知に表示できる行数は限られているので、先頭の3件までにする。
	 *
	 * @param tasks 送る対象のタスク
	 * @param today 今日の日付。今日締切か明日締切かの判定に使う
	 * @return 本文
	 */
	private String buildBody(List<Task> tasks, LocalDate today) {

		// 文字列を何度も足すときは StringBuilder を使う。
		// String の + は、足すたびに新しい文字列を作るため、
		// 繰り返しの中で使うと無駄が積み重なる
		StringBuilder body = new StringBuilder();

		int shown = 0;

		for (Task task : tasks) {

			// 上限に達したら、残りの件数だけ書いて終わる
			if (shown == MAX_LINES) {
				body.append("ほか ").append(tasks.size() - MAX_LINES).append(" 件");
				break;
			}

			// 条件 ? A : B は「条件が成り立てば A、そうでなければ B」という書き方
			String when = task.getDueDate().equals(today) ? "今日" : "明日";

			body.append("[").append(when).append("] ").append(task.getTitle()).append("\n");
			shown++;
		}

		// 末尾の改行を落とす。通知の下に空の行ができてしまうため
		return body.toString().trim();
	}
}