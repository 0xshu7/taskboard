package com.example.taskboard;

// ===== インポート =====
// 以下は Spring Boot の部品。
// SpringApplication      … アプリを起動する道具
// SpringBootApplication  … 起動クラスです、という印
// EnableAsync            … 裏で処理を走らせる機能を有効にする
// EnableScheduling       … 決まった時刻に処理を動かす機能を有効にする

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
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
 * 有効にしている機能は2つ。
 * @EnableAsync      … ProposalService.prepareInBackground が裏で動くために必要
 * @EnableScheduling … NotificationService.checkAndNotify が時刻で動くために必要
 *
 * どちらも、この印が無いと、アノテーションを付けても何も起きない。
 * @Async を付けたのに待たされる、@Scheduled を付けたのに動かない、
 * という形で現れる。エラーは出ない。
 *
 * この2つは専用の設定クラスに分けることもできるが、
 * 印を1つ付けるだけのクラスを増やすより、ここに書くほうが分かりやすい。
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
@EnableAsync
@EnableScheduling
public class TaskboardApplication {

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
		SpringApplication.run(TaskboardApplication.class, args);
	}
}