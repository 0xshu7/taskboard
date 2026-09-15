package com.example.taskboard.service;

// ===== インポート =====
// java.time.LocalDate     … 日付だけ。締切の比較に使う
// java.time.LocalDateTime … 日付＋時刻。いまの時刻を取るのに使う
// java.util.ArrayList     … リストの実装。送る対象を集めるのに使う
// java.util.List          … リストの型。受け取る側はこちらで書く
//
// 以下は Spring の部品。
// Value         … application.yml に書いた値を受け取る
// Scheduled     … 決まった時刻にメソッドを自動で動かす
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下はこのアプリの部品。
// Task           … タスク1件
// TaskStatus     … タスクの状態
// User           … 利用者1人。通知の設定を持っている
// MailSender     … メールを送る係
// TaskRepository … タスクの出し入れ
// UserRepository … 利用者の出し入れ

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.domain.User;
import com.example.taskboard.mail.MailSender;
import com.example.taskboard.repository.TaskRepository;
import com.example.taskboard.repository.UserRepository;

/**
 * 締切が近いタスクをメールで知らせる。
 *
 * 対象は「締切が今日か明日」かつ「未完了」かつ「今日まだ通知していない」タスク。
 *
 * 送るかどうか、いつ送るか、どこへ送るかは、設定画面で決めた値に従う。
 * 設定は利用者ごとに User が持っている（notifyEnabled / notifyTime / notifyEmail）。
 *
 * 送るものが無いときはメールを送らない。
 * 「今日は何もありません」を毎日送ると、そのうち開かれなくなるため。
 *
 * 送ったタスクには通知した日を記録する。
 * 同じ日に何度動かしても、二重に届かない。
 *
 * 入口は2つある。
 *
 * checkAndNotify … 時計から呼ばれる。全員を順に見て、送る時刻の人にだけ送る。
 *                  ブラウザからの要求ではないので、ログインしている人がいない。
 *                  だから「いまの利用者」ではなく、全員を取り出して回す。
 *
 * sendNow        … 画面のボタンから呼ばれる。ログインしている本人にだけ送る。
 *                  設定の ON/OFF も時刻も見ない。文面をその場で確かめるためのもの。
 *
 * 実際に送る処理は sendFor にまとめてあり、2つの入口はどちらもそこを通る。
 * 送る条件や文面を1か所に置くことで、片方だけ直して食い違うことを防いでいる。
 *
 * 送信そのものは MailSender に任せている。
 * mail.mock=true のときはログに出すだけになるので、
 * 開発中に本物のメールを飛ばさずに文面を確認できる。
 */
@Service
public class NotificationService {

	// ===== フィールド =====

	/** タスクの出し入れ */
	private final TaskRepository taskRepository;

	/** 利用者の出し入れ。自動送信で全員を取り出すのに使う */
	private final UserRepository userRepository;

	/** メールを送る係。本物の送信とログ出力を差し替えられる */
	private final MailSender mailSender;

	/** いまログインしている利用者を教えてくれる係。テスト送信で使う */
	private final UserService userService;

	/** メール本文に載せるアプリのURL。application.yml の app.base-url から受け取る */
	private final String baseUrl;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 *
	 * @Value は引数に付ける。${...} の中に application.yml の項目名を書くと、
	 * そこに書かれた値が渡される。コードに直接書かずに済むので、
	 * 公開したときに URL を変えるのが1か所で済む。
	 */
	public NotificationService(TaskRepository taskRepository, UserRepository userRepository,
			MailSender mailSender, UserService userService,
			@Value("${app.base-url}") String baseUrl) {
		this.taskRepository = taskRepository;
		this.userRepository = userRepository;
		this.mailSender = mailSender;
		this.userService = userService;
		this.baseUrl = baseUrl;
	}

	// ===== メソッド（入口） =====

	/**
	 * 1時間ごとに動き、送る時刻になっている利用者に送る。
	 *
	 * cron は左から「秒 分 時 日 月 曜日」の6つ。
	 * "0 0 * * * *" は「毎時0分0秒」という意味。* は「すべて」を表す。
	 *
	 * 時刻を設定で変えられるようにしたため、8時固定ではなく毎時動かし、
	 * 設定した時刻と一致する人にだけ送る形にしている。
	 * 分までは見ず、時だけで判定する（設定画面も時だけを選ばせている）。
	 *
	 * 利用者が増えると、1時間ごとに全員を読み出すことになる。
	 * 通知が ON の人だけを取り出す検索を足せば軽くなるが、
	 * いまの人数では違いが出ないので、そのときに直す。
	 */
	@Scheduled(cron = "0 0 * * * *")
	@Transactional
	public void checkAndNotify() {

		int nowHour = LocalDateTime.now().getHour();

		for (User user : userRepository.findAll()) {

			// 通知が OFF の人は飛ばす
			if (!user.isNotifyEnabled()) {
				continue;
			}

			// 時刻が設定されていなければ送りようがない
			if (user.getNotifyTime() == null) {
				continue;
			}

			// いまが設定した時刻でなければ、次の回に回す
			if (nowHour != user.getNotifyTime().getHour()) {
				continue;
			}

			sendFor(user);
		}
	}

	/**
	 * いまログインしている人に、締切が近いタスクを送る。
	 *
	 * 設定の ON/OFF と時刻は見ない。押されたら必ず処理する。
	 * 文面と送り先をその場で確かめるためのもの。
	 *
	 * @Transactional を付けているのは、最後に通知日を書き込むため。
	 * 途中で失敗した場合、書き込みをすべて取り消して、次回もう一度送れるようにする。
	 */
	@Transactional
	public void sendNow() {
		sendFor(userService.current());
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * ある利用者について、締切が近いタスクを1通のメールにまとめて送る。
	 *
	 * 送るものが無ければ、何もせずに終わる。
	 *
	 * @param user 送る相手
	 */
	private void sendFor(User user) {

		LocalDate today = LocalDate.now();
		LocalDate tomorrow = today.plusDays(1);

		// 締切が今日か明日で、まだ完了していないタスクを取る
		List<Task> candidates = taskRepository
				.findByUserIdAndStatusNotAndDueDateBetweenOrderByDueDateAsc(
						user.getId(), TaskStatus.DONE, today, tomorrow);

		// このうち、今日まだ通知していないものだけを選ぶ
		List<Task> targets = new ArrayList<>();

		for (Task task : candidates) {
			// 通知したことがない、または最後に通知したのが今日ではない
			if (task.getNotifiedDate() == null || !task.getNotifiedDate().equals(today)) {
				targets.add(task);
			}
		}

		// 送るものが無ければ、メールは送らない
		if (targets.isEmpty()) {
			return;
		}

		mailSender.send(resolveMailTo(user), buildSubject(targets), buildBody(targets, today));

		// 送ったタスクに「今日通知した」と記録する。
		// 送信してから記録する順番にしている。
		// 逆にすると、送信に失敗したのに「送った」と残ってしまう。
		for (Task task : targets) {
			task.setNotifiedDate(today);
			taskRepository.save(task);
		}
	}

	/**
	 * 送り先を決める。
	 *
	 * 設定画面で通知先を入れていればそちらへ、空なら登録アドレスへ送る。
	 *
	 * @param user 利用者
	 * @return 送り先のメールアドレス
	 */
	private String resolveMailTo(User user) {
		String notifyEmail = user.getNotifyEmail();

		if (notifyEmail == null || notifyEmail.isBlank()) {
			return user.getEmail();
		}
		return notifyEmail;
	}

	/**
	 * 件名を作る。
	 *
	 * 開く前に件数が分かるようにしている。
	 * 開かなくても状況が伝わると、通知そのものが役に立つ。
	 *
	 * @param tasks 送る対象のタスク
	 * @return 件名
	 */
	private String buildSubject(List<Task> tasks) {
		return "【Task Board】締切が近いタスクが " + tasks.size() + " 件あります";
	}

	/**
	 * 本文を作る。
	 *
	 * 事実（タスク名・締切・難易度）だけを並べる。
	 * 急かす表現は入れない。締切が近いことは、書かなくても本人が一番分かっている。
	 *
	 * @param tasks 送る対象のタスク
	 * @param today 今日の日付。今日締切か明日締切かの判定に使う
	 * @return 本文
	 */
	private String buildBody(List<Task> tasks, LocalDate today) {

		// 文字列を何度も足すときは StringBuilder を使う。
		// String の + は、足すたびに新しい文字列を作るため、
		// 繰り返しの中で使うと無駄が積み重なる。
		StringBuilder body = new StringBuilder();

		body.append("締切が近いタスクです。\n\n");

		for (Task task : tasks) {

			// 今日が締切か、明日が締切かで見出しを変える。
			// 条件 ? A : B は「条件が成り立てば A、そうでなければ B」という書き方。
			String when = task.getDueDate().equals(today) ? "【今日まで】" : "【明日まで】";

			body.append(when)
			    .append(task.getTitle())
			    .append("（難易度 ")
			    .append(task.getDifficulty().getLabel())
			    .append("）\n");
		}

		body.append("\n迷っているより、まず実行。ほんの少しでも。\n");
		body.append(baseUrl).append("\n");

		return body.toString();
	}
}