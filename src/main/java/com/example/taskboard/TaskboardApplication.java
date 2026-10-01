package com.example.taskboard;

// ===== インポート =====
// java.util.TimeZone … 時間帯の設定。起動時に既定を決める
//
// 以下は Spring Boot の部品。
// SpringApplication      … アプリを起動する道具
// SpringBootApplication  … 起動クラスです、という印
// EnableScheduling       … 決まった時刻に処理を動かす機能を有効にする

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * アプリの起点。ここから起動する。
 *
 * このクラスは com.example.taskboard の直下に置く。
 * Spring は、このクラスがある場所とその下のパッケージを探して、
 * @Controller や @Service が付いたクラスを集める。
 * 上の階層に置くと、探す範囲が広がりすぎる。
 * 別の枝に置くと、見つけてもらえない。
 *
 * 有効にしている機能は1つ。
 * @EnableScheduling … NotificationService.checkAndNotify と
 *                     AutoCompleteService.completeDueTasks が時刻で動くために必要
 *
 * この印が無いと、@Scheduled を付けても何も起きない。
 * 動かないだけでエラーは出ないので、気づくのが遅れる。
 *
 * 【@EnableAsync を外した】
 *
 * 以前は事前生成（スタート画面を開いた時点で裏に3案を作っておく仕組み）のために
 * @Async を使っていたので、この印も必要だった。
 * その仕組みをやめたときにメソッドは消したが、印だけが残っていた。
 *
 * 何も有効にしていない印は、残しておくと「ここでは裏で動く処理がある」と
 * 読む人に思わせる。動くものが無いのに、探すことになる。
 *
 * 【時間帯をここで決める】
 *
 * 決めずにいると、動かす場所のOSの設定がそのまま使われる。
 * 開発機は日本時間だが、クラウドや Docker の中では UTC になっていることが多い。
 * そうなると、日付の変わり目が9時間ずれる。
 *
 * ずれ方がやっかいなのは、片方だけ直すと食い違いが生まれるところ。
 *
 * 　@Scheduled(cron = "0 0 8 * * *") … 動く時刻
 * 　LocalDate.now()                  … 「今日」の判定
 * 　LocalDateTime.now()              … 予約時刻、提案の時間帯の判定
 *
 * この3つは別々の仕組みなので、1か所だけ直すと
 * 「UTCの8時に起きて、日本の日付で締切を探す」という、どちらでもない状態になる。
 *
 * 起動前に既定を決めてしまえば、3つとも同じ時刻で動く。
 * データベースへ渡す日時も同じ設定を使うので、保存した値と読んだ値もそろう。
 *
 * SpringApplication.run より前に書くのは、Spring が動き出したあとでは
 * すでにこの設定を読んで用意されているものがあるため。
 */

// ===== アノテーション =====
// @SpringBootApplication … 実は3つの印をまとめたもの。
//   @SpringBootConfiguration … 設定を書けるクラスにする
//   @EnableAutoConfiguration … 入っている部品を見て、必要な準備を自動で行う
//   @ComponentScan           … このクラスの場所より下を探し、印の付いたクラスを集める
//
//   データベースの部品が入っていれば接続を用意し、Web の部品が入っていれば
//   サーバを起動する。設定ファイルをほとんど書かずに動くのは、この仕組みによる。

@SpringBootApplication
@EnableScheduling
public class TaskboardApplication {

	/** アプリ全体で使う時間帯。日付の変わり目と、毎朝8時の基準になる */
	private static final String ZONE = "Asia/Tokyo";

	/**
	 * アプリを起動する。
	 *
	 * SpringApplication.run が、次のことを順に行う。
	 * 1. このクラスの場所より下を探し、印の付いたクラスを集める
	 * 2. 集めたクラスを1つずつ作り、必要なものを互いに渡す
	 * 3. データベースへ接続し、テーブルを用意する
	 * 4. サーバを起動し、要求を受け付けられる状態にする
	 *
	 * コンストラクタを自分で呼ばずに済んでいるのは、2で
	 * Spring がまとめて用意しているため。
	 *
	 * @param args 起動時のコマンドライン引数
	 */
	public static void main(String[] args) {

		// 動かす場所のOSの設定に左右されないようにする。
		// run より前に決めるので、このあと作られるものはすべてこの時間帯で動く
		TimeZone.setDefault(TimeZone.getTimeZone(ZONE));

		SpringApplication.run(TaskboardApplication.class, args);
	}
}