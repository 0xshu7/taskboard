package com.example.taskboard.domain;

// ===== インポート =====
// java.time.LocalDateTime … 日付＋時刻。押した時刻と決めた時刻に使う
//
// 以下は JPA の部品。
// Entity         … このクラスはテーブルに対応する、という印
// Table          … 対応するテーブル名を指定する
// Id             … 主キーの印
// GeneratedValue … 番号を自動で振らせる
// GenerationType … その振り方の種類（IDENTITY を使う）
// Column         … 列名・空の可否・文字数の上限・列の型を指定する
// Enumerated     … 列挙型をどう保存するかを指定する
// EnumType       … その保存の仕方（STRING を使う）

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * スタートを1回押したときの「提案1セット」。proposal_sets テーブルの1行にあたる。
 *
 * 1セットにつき ProposalCard が3枚ぶら下がる。
 * カード側が proposalSetId でこのセットのIDを持つ形でつながっている。
 *
 * このクラスは3案そのものではなく、「いつ・どうやって・どうなったか」を持つ。
 *
 * 記録している内容は3種類。
 * 1. 時間  … requestedAt と decidedAt。その差が迷い時間になる
 * 2. 出所  … source。AI が答えたのか、失敗して固定3案を出したのか
 * 3. 文面  … promptText。そのとき送ったプロンプトの全文
 *
 * 2と3があることで、あとから「どの版のプロンプトが、どんな3案を出したか」を
 * 突き合わせられる。プロンプトを改良していくための材料になる。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言
// @Table  … 対応するテーブル名

@Entity
@Table(name = "proposal_sets")
public class ProposalSet {

	// ===== フィールド =====

	/** 主キー。保存する前は番号が未定なので、null を入れられる Long にする */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 誰のものか。users テーブルの id を指す */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/**
	 * スタートを押した時刻。new した時点の時刻がそのまま入る。
	 *
	 * 事前生成したセットを再利用するときは、押された時刻に入れ直す。
	 * 作った時刻のままでは、迷い時間に待ち時間が混ざってしまうため。
	 *
	 * この列の日付を重複なく数えたものが、実績画面の「来た日数」になる。
	 */
	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt = LocalDateTime.now();

	/**
	 * どれかを選んだ、または別の話題を押した時刻。まだなら null。
	 *
	 * requestedAt との差が「迷い時間」になる。
	 * 何秒で迷いと見なすかはアプリ側では決めず、秒数をそのままプロンプトへ渡す。
	 * 同じ判断を2か所に持つと食い違うため、解釈は AI に任せる。
	 *
	 * ただし上限だけは設ける。長すぎる場合は席を外していたとみなし、
	 * プロンプトへ渡さない（app.hesitation.ignore-seconds）。
	 */
	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	/**
	 * どんな状況で出した提案か。現在は NORMAL のみ。
	 *
	 * 「久しぶりに開いた」「試験が近い」などで出し分ける拡張を見込んで列だけ用意している。
	 * 値が増えたときは列挙型に変える。
	 */
	@Column(length = 20, nullable = false)
	private String mode = "NORMAL";

	/**
	 * この3案を誰が作ったか。MOCK / AI / FALLBACK のいずれか。
	 *
	 * MOCK     … 開発用の固定3案（ai.mock=true）
	 * AI       … Gemini が返した3案
	 * FALLBACK … AI の呼び出しに失敗し、固定3案で代替した
	 *
	 * FALLBACK の割合を数えれば、AI がどのくらい失敗しているかを後から測れる。
	 */
	@Column(length = 10, nullable = false)
	private String source = "MOCK";

	/**
	 * このセットの状態。OPEN / CHOSEN / RETOPIC / EXPIRED。
	 *
	 * OPEN のものだけが再利用できる。詳しくは ProposalStatus を参照。
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ProposalStatus status = ProposalStatus.OPEN;

	/**
	 * 実際に AI へ送ったプロンプトの全文。
	 *
	 * プロンプトは版を重ねて改良していくため、「この3案が出たときの文章」を
	 * そのまま残しておく。あとから版ごとの出力を比較できる。
	 *
	 * columnDefinition = "TEXT" は、列の型を直接指定する書き方。
	 * 通常の VARCHAR には長さの上限があるが、プロンプトは数千文字になるため
	 * 長い文章を入れられる TEXT にしている。
	 */
	@Column(name = "prompt_text", columnDefinition = "TEXT")
	private String promptText;

	// ===== メソッド =====
	// 中身を読み書きするだけ。計算や判断はここではしない。

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** 主キーを入れる。ふつうはデータベースが振るので、自分で呼ぶことはない */
	public void setId(Long id) {
		this.id = id;
	}

	/** 誰の提案かを返す */
	public Long getUserId() {
		return userId;
	}

	/** 誰の提案かを入れる */
	public void setUserId(Long userId) {
		this.userId = userId;
	}

	/** スタートを押した時刻を返す */
	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	/** スタートを押した時刻を入れる。事前生成を再利用するとき入れ直す */
	public void setRequestedAt(LocalDateTime requestedAt) {
		this.requestedAt = requestedAt;
	}

	/** 決めた時刻を返す。まだ決めていなければ null */
	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	/** 決めた時刻を入れる */
	public void setDecidedAt(LocalDateTime decidedAt) {
		this.decidedAt = decidedAt;
	}

	/** どんな状況で出した提案かを返す */
	public String getMode() {
		return mode;
	}

	/** どんな状況で出した提案かを入れる */
	public void setMode(String mode) {
		this.mode = mode;
	}

	/** 誰が作った3案かを返す。MOCK / AI / FALLBACK */
	public String getSource() {
		return source;
	}

	/** 誰が作った3案かを入れる */
	public void setSource(String source) {
		this.source = source;
	}

	/** セットの状態を返す */
	public ProposalStatus getStatus() {
		return status;
	}

	/** セットの状態を入れる */
	public void setStatus(ProposalStatus status) {
		this.status = status;
	}

	/** 送ったプロンプトの全文を返す */
	public String getPromptText() {
		return promptText;
	}

	/** 送ったプロンプトの全文を入れる */
	public void setPromptText(String promptText) {
		this.promptText = promptText;
	}
}