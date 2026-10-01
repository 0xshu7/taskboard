package com.example.taskboard.service;

// ===== インポート =====
// java.util.List … リストの型
//
// 以下はログの部品。Spring Boot に最初から入っているので、pom.xml に足すものは無い。
// Logger        … ログを出す道具
// LoggerFactory … そのクラス用の Logger を作る
//
// 以下は Spring の部品。
// Scheduled … 決まった間隔でメソッドを自動で動かす
// Service   … 処理のまとまりです、という印
//
// 以下はこのアプリの部品。
// Task           … タスク1件
// UserRepository … 利用者の出し入れ

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.taskboard.domain.Task;
import com.example.taskboard.repository.UserRepository;

/**
 * 「やっちゃう」で予約されたタスクを、時間が来たら完了にする。
 *
 * 画面のタイマーではなく、サーバー側で時刻を見ている。
 * ブラウザのタイマーだと、閉じた瞬間に止まってしまう。
 * こちらで持てば、押してアプリを閉じて、実際に手を動かしている間に完了し、
 * 通知だけが届く、という流れになる。
 *
 * この仕組みを入れた理由は、完了ボタンの押し忘れを減らすこと。
 * 始めるときに1回、終わったときにもう1回押す必要があると、2回目を忘れる。
 * 「やっちゃう」は、始めると同時に終わりを予約する。
 *
 * 【このクラスは2つを順に呼ぶだけ】
 *
 * 1. TaskService.completeDue() … データベースを書き換える。短く終わる
 * 2. NotificationService.send() … 外部へ送る。相手の応答を待つ
 *
 * 1 のほうにだけ @Transactional が付いている。
 * まとめて1つのメソッドに入れて @Transactional を付けると、
 * 通知を送っているあいだずっとデータベースの接続を握ったままになる。
 * 完了したタスクの数だけ繰り返すので、待ち時間が積み上がる。
 *
 * かといって、このクラスの中で切り出しても意味がない。
 * **同じクラスの中から自分のメソッドを呼ぶと @Transactional は効かない。**
 * Spring は外から呼ばれたときだけ横取りできるため。@Async が効かなかったのと同じ理由。
 *
 * そこで、データベース側の処理は TaskService に置いてある。
 * 別のクラスなので、呼べばきちんと効く。
 *
 * 完了したあとに通知が失敗しても、完了は取り消されない。
 * 終わったことのほうが事実で、知らせられなかったのは別の話だから。
 */
@Service
public class AutoCompleteService {

	// ===== フィールド =====

	/**
	 * ログを出す道具。
	 *
	 * 以前は System.out.println を使い、目印に「###」を付けて探していた。
	 * ロガーはクラス名を自動で付けるので、目印は要らない。
	 *
	 * 10秒ごとに動くクラスなので、普段は静かにしておきたい。
	 * 出すのは実際に完了したときだけで、対象が無ければ1行も出ない。
	 */
	private static final Logger log = LoggerFactory.getLogger(AutoCompleteService.class);

	/** タスクの処理。時間が来たタスクを完了にしてもらう */
	private final TaskService taskService;

	/** 利用者の出し入れ。通知の送り先を引くのに使う */
	private final UserRepository userRepository;

	/** 通知を送る係。送り方はあちらに1か所だけ持たせてある */
	private final NotificationService notificationService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public AutoCompleteService(TaskService taskService,
			UserRepository userRepository,
			NotificationService notificationService) {

		this.taskService = taskService;
		this.userRepository = userRepository;
		this.notificationService = notificationService;
	}

	// ===== メソッド =====

	/**
	 * 時間が来たタスクを完了にして、知らせる。
	 *
	 * fixedDelay は「前回が終わってから次までの間隔」。
	 * fixedRate（開始から開始まで）と違い、処理が長引いても重ならない。
	 *
	 * 10秒ごとにしているのは、デモで divisor を 60 にしたときのため。
	 * 5分のタスクが5秒で終わる設定なので、間隔が長いと待たされる。
	 * 対象が無ければ検索1回で終わるので、負荷はほとんどない。
	 *
	 * ブラウザからの要求ではないので、ログインしている人がいない。
	 * だから「いまの利用者」ではなく、タスクが持つ userId から引く。
	 */
	@Scheduled(fixedDelay = 10000)
	public void completeDueTasks() {

		// ここでデータベースの書き換えが終わる。接続はこの1行のあいだだけ握る
		List<Task> completed = taskService.completeDue();

		for (Task task : completed) {

			// {} のところに、後ろの値が入る。
			// 文字を + でつなぐと、ログを出さない設定のときも先につなぐ手間がかかる
			log.info("おまかせ完了: {}", task.getTitle());

			// 通知する。届け先を登録していない人には、何も起きない
			userRepository.findById(task.getUserId()).ifPresent(user ->
					notificationService.send(user, "おまかせ完了",
							task.getTitle() + "\nを完了にしました"));
		}
	}
}