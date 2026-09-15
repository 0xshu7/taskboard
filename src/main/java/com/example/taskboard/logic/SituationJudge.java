package com.example.taskboard.logic;

// ===== インポート =====
// java.time.LocalTime … 時刻だけ。いまが何時かを受け取る
//
// 以下は Spring の部品。
// Component … Spring に管理させるクラスです、という印
//
// 以下はこのアプリの部品。
// Granularity … タスクの粒度（5分でできる / 今日中 / まとまった時間）

import java.time.LocalTime;

import org.springframework.stereotype.Component;

import com.example.taskboard.domain.Granularity;

/**
 * いまの時刻から「どんな提案が向いているか」を決める。
 *
 * 時間帯によって、動ける量は変わる。
 * 朝はこれから1日が始まるので、軽いもので勢いがつく。
 * 夜は疲れているので、重いものを出しても続かない。
 * 深夜に30分の作業を勧めれば、翌日に響く。
 *
 * これは誰にとっても大きくは変わらないため、AIに任せず、こちらで決めている。
 * AIに渡すのは「その人に合うかどうか」のような、人によって違う判断だけにする。
 *
 * 判断した結果は Situation にまとめ、PromptBuilder がプロンプトへ書き込む。
 * 時間帯の名前と一言を渡し、実際の案を考えるのはAIの役目になる。
 *
 * 外部とのやりとりもデータベースへの保存も行わない。
 * 時刻を渡せば必ず同じ結果が返るので、単体テストが書きやすい。
 */

// ===== アノテーション =====
// @Component … Spring に管理させるクラスです、という印。
//               @Service と働きは同じだが、こちらは「処理のまとまり」ではなく
//               「部品」という位置づけなので @Component を使っている。

@Component
public class SituationJudge {

	// ===== メソッド =====

	/**
	 * 時刻から状況を判断する。
	 *
	 * 引数で時刻を受け取り、中で LocalTime.now() を呼んでいない。
	 * 中で現在時刻を取ると、テストのときに時間帯を指定できなくなる。
	 * 「朝ならこう返る」を確かめるために、外から渡す形にしている。
	 *
	 * 区切りは次のとおり。
	 *  5時〜11時 … 朝
	 * 11時〜17時 … 昼
	 * 17時〜22時 … 夜
	 * それ以外    … 深夜
	 *
	 * 朝・夜・深夜がどれも FIVE_MIN なのは、軽さを求める理由が違うため。
	 * 朝は勢いをつけるため、夜は疲れているため、深夜は翌日に響かせないため。
	 * 同じ重さでも、渡す一言が違うので、AIの提案は変わる。
	 *
	 * @param now いまの時刻
	 * @return 判断した状況
	 */
	public Situation judge(LocalTime now) {

		int hour = now.getHour();

		// 朝：これから1日が始まる。軽いもので勢いをつける
		if (hour >= 5 && hour < 11) {
			return new Situation("朝", Granularity.FIVE_MIN,
					"朝なので、短時間で終わって勢いがつくものがよい");
		}

		// 昼：まとまった時間が取りやすい
		if (hour >= 11 && hour < 17) {
			return new Situation("昼", Granularity.TODAY,
					"日中なので、今日中に終えられる程度のものがよい");
		}

		// 夜：疲れている。重いものは続かない
		if (hour >= 17 && hour < 22) {
			return new Situation("夜", Granularity.FIVE_MIN,
					"夜なので、疲れていても手が動く軽いものがよい");
		}

		// 深夜：明日に響かないものだけ
		return new Situation("深夜", Granularity.FIVE_MIN,
				"深夜なので、明日に響かない5分程度のものだけにする");
	}
}