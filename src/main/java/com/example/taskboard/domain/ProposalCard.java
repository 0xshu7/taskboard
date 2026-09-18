package com.example.taskboard.domain;

// ===== インポート =====
// すべて JPA の部品。日付を扱わないため java.time は使わない。
//
// Entity         … このクラスはテーブルに対応する、という印
// Table          … 対応するテーブル名を指定する
// Id             … 主キーの印
// GeneratedValue … 番号を自動で振らせる
// GenerationType … その振り方の種類（IDENTITY を使う）
// Column         … 列名・空の可否・文字数の上限を指定する
// Enumerated     … 列挙型をどう保存するかを指定する
// EnumType       … その保存の仕方（STRING を使う）

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
 * 3枚目は手が止まっている原因そのものを片づけるもの。
 * どれを選ぶかで、その日の自分の状態を申告することになる。
 *
 * 選ばれたカードは TaskService.createFromCard によって Task に写される。
 * 写されるのは title・detail・difficulty で、それ以外はカード側に残る。
 *
 * カードは選ばれなくても消さずに残す。
 * 「何を出して、何が選ばれなかったか」が、提案の質を測る材料になるため。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言
// @Table  … 対応するテーブル名

@Entity
@Table(name = "proposal_cards")
public class ProposalCard {

	// ===== フィールド =====

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
	 */
	@Column(nullable = false, length = 100)
	private String title;

	/** 生活 / 勉強 / 食事 / 休日 など。どんな種類の案かを表す */
	@Column(length = 30)
	private String category;

	/**
	 * 選んだあとに見る、具体的な手順と終わりの線。
	 *
	 * 改行区切りの1本の文字列で、最後の行は必ず「終わり：」で始まる。
	 * その状態になったら本人の判断を待たずに終わり、と言い切れる文にする。
	 *
	 * 配列で持つとテーブルがもう1つ必要になるため、1本の文字列にして
	 * 画面側の white-space: pre-line で改行として見せている。
	 */
	@Column(length = 300)
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
	@Column(length = 200)
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

	
	// ===== メソッド =====
	// 中身を読み書きするだけ。計算や判断はここではしない。
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

	/** 見出しを入れる */
	public void setTitle(String title) {
		this.title = title;
	}

	/** 種類を返す */
	public String getCategory() {
		return category;
	}

	/** 種類を入れる */
	public void setCategory(String category) {
		this.category = category;
	}

	/** 手順と終わりの線を返す */
	public String getDetail() {
		return detail;
	}

	/** 手順と終わりの線を入れる */
	public void setDetail(String detail) {
		this.detail = detail;
	}

	/** 大きさの区分を返す */
	public Granularity getGranularity() {
		return granularity;
	}

	/** 大きさの区分を入れる */
	public void setGranularity(Granularity granularity) {
		this.granularity = granularity;
	}

	/** 難易度を返す */
	public Difficulty getDifficulty() {
		return difficulty;
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

	/** 勧める理由を入れる */
	public void setReason(String reason) {
		this.reason = reason;
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
}