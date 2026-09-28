package com.example.taskboard.ai;

// ===== インポート =====
// java.util.List … リストの型。3枚まとめて返すのに使う
//
// 以下は Spring の部品。
// ConditionalOnProperty … 設定の値によって、このクラスを使うかどうかを決める
// Component             … Spring に管理させるクラスです、という印
//
// 以下はこのアプリの部品。
// ProposalCard … 提案カード1枚

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.taskboard.domain.ProposalCard;

/**
 * 決まった3案を返すクラス。AI は呼ばない。
 *
 * ai.mock=true のときだけ使われる。
 *
 * これがあることで、AI を呼ばずに画面と保存の流れを作りきれる。
 * 実際、スタートから3案の表示、選択、タスク化、実績への反映までは、
 * すべてこの状態で作った。AI をつないだのは最後になる。
 *
 * 利点は4つ。
 * 1. 無料枠が減らない
 * 2. 応答を待たないので、画面の確認が速い
 * 3. 中身が毎回同じなので、見た目の調整がしやすい
 * 4. ネットワークが無くても動く
 *
 * 中身は FallbackCards と共通にしてある。
 * 同じ3案を2か所に書くと、片方だけ直して食い違う。
 *
 * 渡されたプロンプトは使わない。
 * 使わないが、受け取る形はインターフェースの決まりなので変えられない。
 * 本物と同じ呼び方ができることのほうが大事になる。
 */

// ===== アノテーション =====
// @Component … Spring に管理させるクラスです、という印。
//
// @ConditionalOnProperty … ai.mock が true のときだけ、このクラスを使う。
//   RealProposalGenerator には havingValue="false" が付いているので、
//   どちらか一方だけが選ばれ、ProposalGenerator の中身が必ず1つに決まる。

@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "true")
public class MockProposalGenerator implements ProposalGenerator {

	// ===== メソッド =====

	/**
	 * 決まった3案を返す。
	 *
	 * 引数の prompt は受け取るが使わない。
	 * AI に何を伝えるかに関係なく、同じ3案が返る。
	 *
	 * @Override は「インターフェースで決められたメソッドを実装している」という印。
	 * 綴りや引数を間違えると、この印があることで気づける。
	 *
	 * @param prompt AI に送る文章。ここでは使わない
	 * @return 決まった3案
	 */
	@Override
	public List<ProposalCard> generate(String prompt) {
		// モックは状況を持たない。FallbackCards 側が昼として扱う
		return FallbackCards.create(null, null);
	}

	/**
	 * どこから来た3案かを表す文字を返す。
	 *
	 * ProposalSet.source に保存され、
	 * あとから「どの経路で作られた3案か」を数えられる。
	 *
	 * @return "MOCK"
	 */
	@Override
	public String sourceName() {
		return "MOCK";
	}
}