package com.example.taskboard.domain;

// ===== インポート =====
// java.util.Objects … null でも落ちずに比べる道具。equals で使う
//
// 以下は JPA の部品。日付を扱わないため java.time は使わない。
//
// Entity         … このクラスはテーブルに対応する、という印
// Table          … 対応するテーブル名を指定する
// Id             … 主キーの印
// GeneratedValue … 番号を自動で振らせる
// GenerationType … その振り方の種類（IDENTITY を使う）
// Column         … 列名・空の可否・文字数の上限を指定する
// Enumerated     … 列挙型をどう保存するかを指定する
// EnumType       … その保存の仕方（STRING を使う）

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 提案カード1枚。proposal_cards テーブルの1行にあたる。
 * 1つの ProposalSet につき3枚できる。
 *
 * 3枚は重さが異なり、position がその順番を表す。
 * 1枚目は数分で終わるもの、2枚目は今日中に終わって進みが見えるもの、
 * 3枚目はリストにあるタスクをそのまま使ったもの。
 * どれを選ぶかで、その日の自分の状態を申告することになる。
 *
 * 選ばれたカードは TaskService.createFromCard によって Task に写される。
 * 写されるのは title・detail・difficulty・estimatedMinutes で、
 * それ以外はカード側に残る。
 *
 * sourceTaskId が入っているカードは写さない。
 * 元のタスクを進行中にするだけで、新しいタスクは作らない。
 *
 * カードは選ばれなくても消さずに残す。
 * 「何を出して、何が選ばれなかったか」が、提案の質を測る材料になるため。
 *
 * 文字数の上限は setter で守っている。
 * 中身はAIが返すものなので、長さをこちらで決められないため。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言
// @Table  … 対応するテーブル名

@Entity
@Table(name = "proposal_cards")
public class ProposalCard {

	// ===== フィールド =====

	/** 見出しの上限。列の length と同じ値にしておく */
	private static final int TITLE_MAX = 100;

	/** 種類の上限 */
	private static final int CATEGORY_MAX = 30;

	/** 手順と終わりの目安の上限 */
	private static final int DETAIL_MAX = 300;

	/** 勧める理由の上限 */
	private static final int REASON_MAX = 200;

	/** 主キー。保存する前は番号が未定なので、null を入れられる Long にする */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * どのセットのカードか。ProposalSet の id が入る。
	 *
	 * ProposalSet 型ではなく数字で持つ。
	 * クラス同士を直接つなぐ書き方もあるが、読み込みの範囲が広がり
	 * 挙動が追いにくくなるため、ここでは使わない。
	 */
	@Column(name = "proposal_set_id", nullable = false)
	private Long proposalSetId;

	/**
	 * 何番目のカードか。1〜3。
	 *
	 * 表示順であると同時に、重さの役割も表す。
	 * 画面では 'card-' + position でCSSクラス名を作り、3枚を色分けしている。
	 */
	@Column(nullable = false)
	private Integer position;

	/**
	 * カードの見出し。選ぶときに読む文。
	 *
	 * detail の要約である必要はない。何の案かが一目で分かる短さを優先する。
	 * 「前置き＋やること」の形で、頭に時間帯の言葉が入る。
	 */
	@Column(nullable = false, length = TITLE_MAX)
	private String title;

	/**
	 * どんな分野の案か。べんきょう / せいかつ / リラックス。
	 *
	 * 1枚目と2枚目は SituationJudge が決めたものがそのまま入る。
	 * 3枚目は既存タスクなので、AI が内容から判断する。
	 */
	@Column(length = CATEGORY_MAX)
	private String category;

	/**
	 * 選んだあとに見る、具体的な手順と終わりの目安。
	 *
	 * 改行区切りの1本の文字列で、「✓」で始まる行が3つ、
	 * 最後の行は「おわりの目安: 」で始まる。
	 * その状態になったら本人の判断を待たずに終わり、と言い切れる文にする。
	 *
	 * 配列で持つとテーブルがもう1つ必要になるため、1本の文字列にして
	 * 画面側の white-space: pre-line で改行として見せている。
	 */
	@Column(length = DETAIL_MAX)
	private String detail;

	/** 大きさの区分。FIVE_MIN / TODAY / GROUP */
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Granularity granularity;

	/** 難易度。低・中・高 */
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Difficulty difficulty;

	/**
	 * かかる時間の目安（分）。
	 *
	 * 3枚でばらつかせる。近い値が並ぶと、どれを選んでも同じになり
	 * 3つから選ぶ意味が失われる。
	 */
	@Column(name = "estimated_minutes")
	private Integer estimatedMinutes;

	/**
	 * なぜ今これを勧めるのかの一言。
	 *
	 * 同じ案でも、理由が添えられているかどうかで受け取り方が変わる。
	 * 評価や励ましは書かず、いま選ぶ手がかりだけを短く示す。
	 */
	@Column(length = REASON_MAX)
	private String reason;

	/**
	 * このカードが選ばれたか。
	 *
	 * 3枚のうち1枚だけが true になる。
	 * 選ばれなかった2枚も残すことで、あとから傾向を調べられる。
	 */
	@Column(nullable = false)
	private boolean chosen = false;

	/**
	 * 元になったタスクのID。
	 *
	 * 新しい案なら null。
	 * リストにあるタスクを拾ってきた案なら、そのタスクのIDが入る。
	 *
	 * 選ばれたときの動きが、この値で変わる。
	 * null なら新しくタスクを作り、値があればそのタスクを進行中にする。
	 *
	 * 画面のバッジ（あたらしい／リストから）も、この値だけを見て決めている。
	 * 見た目のためだけの項目を別に持つと、中の動きと食い違うため。
	 */
	@Column(name = "source_task_id")
	private Long sourceTaskId;

	/**
	 * このカードから動き出したタスクのID。選ばれるまでは null。
	 *
	 * sourceTaskId とは向きが違う。
	 * 　sourceTaskId … 提案を作るときに「この既存タスクを使った」
	 * 　resultTaskId … 選ばれたときに「このタスクになった」
	 *
	 * 新しい案なら作られたタスク、既存タスクを使った案ならそのタスクが入る。
	 * どちらも同じ形で入るので、あとの集計で場合分けが要らない。
	 *
	 * これが無いと「選ばれて、実際に完了したか」を数えられない。
	 * 選択だけを数えると、選びやすいだけの提案に寄ってしまう。
	 * このアプリの目的は選ばせることではなく、動けることなので、
	 * 完了まで追えるようにしておく。
	 */
	@Column(name = "result_task_id")
	private Long resultTaskId;

	// ===== メソッド（getter / setter） =====
	// 中身を読み書きするだけ。計算や判断はここではしない。
	// ただし、文字数の上限だけは setter で面倒を見ている。
	//
	// boolean の getter は get ではなく is で始める。
	// Thymeleaf も Spring も isXxx() を探しに行くため、get では画面から読めない。

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** 主キーを入れる。ふつうはデータベースが振るので、自分で呼ぶことはない */
	public void setId(Long id) {
		this.id = id;
	}

	/** どのセットのカードかを返す */
	public Long getProposalSetId() {
		return proposalSetId;
	}

	/** どのセットのカードかを入れる。セットを保存した直後に設定する */
	public void setProposalSetId(Long proposalSetId) {
		this.proposalSetId = proposalSetId;
	}

	/** 何番目のカードかを返す。1〜3 */
	public Integer getPosition() {
		return position;
	}

	/** 何番目のカードかを入れる */
	public void setPosition(Integer position) {
		this.position = position;
	}

	/** 見出しを返す */
	public String getTitle() {
		return title;
	}

	/**
	 * 見出しを入れる。上限を超えたら切り詰める。
	 *
	 * AIが返すものなので、長さをこちらで決められない。
	 * プロンプトで30字と指定しているが、守られる保証はない。
	 */
	public void setTitle(String title) {
		this.title = truncate(title, TITLE_MAX);
	}

	/** 分野を返す */
	public String getCategory() {
		return category;
	}

	/** 分野を入れる。上限を超えたら切り詰める */
	public void setCategory(String category) {
		this.category = truncate(category, CATEGORY_MAX);
	}

	/** 手順と終わりの目安を返す */
	public String getDetail() {
		return detail;
	}

	/**
	 * 手順と終わりの目安を入れる。上限を超えたら切り詰める。
	 *
	 * 超えたまま保存すると、列の上限に引っかかって例外で止まる。
	 * 3案のうち1枚が長かっただけでスタート全体が失敗し、
	 * 保存はフォールバックの外なので固定3案にも切り替わらない。
	 *
	 * 切れた文章が出るほうが、画面が出ないよりましだと判断した。
	 * ただしこれは保険で、本来はプロンプト側で超えさせない（250字を指定）。
	 */
	public void setDetail(String detail) {
		this.detail = truncate(detail, DETAIL_MAX);
	}

	/** 大きさの区分を返す */
	public Granularity getGranularity() {
		return granularity;
	}

	/** 大きさの区分を入れる */
	public void setGranularity(Granularity granularity) {
		this.granularity = granularity;
	}

	/** 難易度を返す。null のときは中を返す */
	public Difficulty getDifficulty() {
		return difficulty != null ? difficulty : Difficulty.MID;
	}

	/** 難易度を入れる */
	public void setDifficulty(Difficulty difficulty) {
		this.difficulty = difficulty;
	}

	/** かかる時間の目安を返す */
	public Integer getEstimatedMinutes() {
		return estimatedMinutes;
	}

	/** かかる時間の目安を入れる */
	public void setEstimatedMinutes(Integer estimatedMinutes) {
		this.estimatedMinutes = estimatedMinutes;
	}

	/** 勧める理由を返す */
	public String getReason() {
		return reason;
	}

	/** 勧める理由を入れる。上限を超えたら切り詰める */
	public void setReason(String reason) {
		this.reason = truncate(reason, REASON_MAX);
	}

	/** 選ばれたかどうかを返す */
	public boolean isChosen() {
		return chosen;
	}

	/** 選ばれたかどうかを入れる */
	public void setChosen(boolean chosen) {
		this.chosen = chosen;
	}

	/** 元になったタスクのIDを返す。新しい案なら null */
	public Long getSourceTaskId() {
		return sourceTaskId;
	}

	/** 元になったタスクのIDを入れる */
	public void setSourceTaskId(Long sourceTaskId) {
		this.sourceTaskId = sourceTaskId;
	}

	/** このカードから動き出したタスクのIDを返す。選ばれるまでは null */
	public Long getResultTaskId() {
		return resultTaskId;
	}

	/** このカードから動き出したタスクのIDを入れる */
	public void setResultTaskId(Long resultTaskId) {
		this.resultTaskId = resultTaskId;
	}

	// ===== メソッド（同じカードかどうかの判定） =====

	/**
	 * 同じカードかどうかを判定する。
	 *
	 * 比べるのは id。Task と同じ考え方で、
	 * カードには「変わらない鍵」が他に無いため。
	 *
	 * id が null のとき（まだ保存していないとき）は false を返す。
	 * 保存前のカードは3枚とも id が無いので、
	 * 比べると全部同じものになってしまう。
	 */
	@Override
	public boolean equals(Object other) {

		if (this == other) {
			return true;
		}
		if (!(other instanceof ProposalCard card)) {
			return false;
		}
		return id != null && Objects.equals(id, card.id);
	}

	/**
	 * ハッシュ値を返す。
	 *
	 * 固定の値を返している。id から作ると、保存の前後で値が変わるため。
	 * 詳しくは Task.hashCode の説明を見ること。
	 */
	@Override
	public int hashCode() {
		return 31;
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 文字数の上限を超えていたら切り詰める。
	 *
	 * Task にも同じものがある。共通のクラスに出すこともできるが、
	 * 7行のために参照されるクラスを1つ増やすと、
	 * パッケージの構成に手が入る。3か所目が出たときに考える。
	 *
	 * @param text 元の文字
	 * @param max  上限の文字数
	 * @return 上限に収まる文字。null ならそのまま null
	 */
	private static String truncate(String text, int max) {

		if (text != null && text.length() > max) {
			return text.substring(0, max);
		}
		return text;
	}
}