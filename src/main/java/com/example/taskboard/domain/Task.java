package com.example.taskboard.domain;

// ===== インポート =====
// 外から借りてくる部品。何のために借りているかを1行ずつ。
//
// java.time.LocalDate     … 日付だけ（2026-09-18）。締切に使う
// java.time.LocalDateTime … 日付＋時刻。作成日時・完了日時・自動完了の予定時刻に使う
// java.util.Objects       … null でも落ちずに比べる道具。equals で使う
//
// 以下は JPA（データベースとJavaをつなぐ仕組み）の部品。
// import は javax ではなく jakarta から始まる。古い記事は javax なので注意。
//
// Entity          … このクラスはテーブルに対応する、という印
// Table           … 対応するテーブル名を指定する
// Id              … 主キーの印
// GeneratedValue  … 番号を自動で振らせる
// GenerationType  … その振り方の種類（IDENTITY を使う）
// Column          … 列名・空の可否・文字数の上限を指定する
// Enumerated      … 列挙型をどう保存するかを指定する
// EnumType        … その保存の仕方（STRING を使う）
// PrePersist      … 保存される直前に動かすメソッドの印

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * タスク1件。tasks テーブルの1行にあたる。
 *
 * 作られ方は2通り。
 * 1. 画面の「追加する」から手で入力する
 * 2. AIの3択から「これにする」を押す（TaskService.createFromCard）
 * 後者には完了のヒントとかかる時間の目安が入る。
 *
 * 状態と完了日時は必ずセットで動く。
 * 完了にしたのに日時が入っていない、完了を取り消したのに日時が残っている、
 * という食い違いが起きると、完了一覧の並び順が狂う。
 * そのため setStatus が完了日時まで面倒を見ており、
 * 完了日時だけを外から入れる手段は用意していない。
 *
 * 文字数の上限も setter で守っている。
 * 画面の maxlength は開発者ツールで外せるし、
 * AIが返す文章の長さはこちらで決められないため。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言。これが無いと表が作られない。
// @Table  … 対応するテーブル名。書かないとクラス名がそのまま使われる（Task → task）。

@Entity
@Table(name = "tasks")
public class Task {

	// ===== フィールド =====
	// ここに書いた項目が、そのままテーブルの列になる。

	/** タスクの内容の上限。列の length と同じ値にしておく */
	private static final int TITLE_MAX = 100;

	/** 完了のヒントの上限。列の length と同じ値にしておく */
	private static final int DETAIL_MAX = 300;

	/** 主キー。保存する前は番号が未定なので、null を入れられる Long にする */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 誰のものか。users テーブルの id を指す */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/**
	 * タスクの内容。
	 * 画面の maxlength だけでは回避できるので、データベース側にも同じ上限を置く。
	 */
	@Column(nullable = false, length = TITLE_MAX)
	private String title;

	/**
	 * 難易度。低・中・高。
	 *
	 * EnumType.STRING が重要。これが無いと並び順の数字（LOW=0, MID=1, HIGH=2）で
	 * 保存され、あとから項目を足したり順番を変えると、保存済みデータの意味がずれる。
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Difficulty difficulty = Difficulty.MID;

	/** 締切。無いタスクもあるので null を許す。時刻は不要なので LocalDate */
	@Column(name = "due_date")
	private LocalDate dueDate;

	/** 状態。未実施 → 進行中 → 完了 と一周する */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TaskStatus status = TaskStatus.TODO;

	/**
	 * 表示順。小さいほど上に出す。
	 * データベースは行の順番を保証しないので、順番を持ちたければ列が要る。
	 */
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;

	/**
	 * 作成日時。
	 *
	 * 初期値をここに書かず、保存の直前に入れている。
	 * 下の onCreate を見ること。
	 */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/**
	 * 完了日時。未完了なら null。
	 *
	 * 完了リストの並び順（新しい順）に使っている。
	 * 入れるのは setStatus だけ。外から直接入れる手段は用意していない。
	 */
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	/**
	 * やり方と「終わりの線」。AIの提案から作ったタスクだけに入る。
	 *
	 * 改行区切りの1本の文字列。最後の行は必ず「終わり：」で始まる。
	 * 配列にするとテーブルがもう1つ必要になるので、1本の文字列にして、
	 * 画面側の white-space: pre-line で改行として見せている。
	 *
	 * nullable = false を付けていないのは、既にデータがある表に
	 * NOT NULL の列を後から足せないため。
	 */
	@Column(length = DETAIL_MAX)
	private String detail;

	/**
	 * かかる時間の目安（分）。
	 *
	 * AIの提案から作ったタスクにはカードの値が入る。
	 * 手で追加したタスクには入らないので null になる。
	 *
	 * 「やっちゃう」で自動完了を予約するときに、何分後にするかの元になる。
	 * null のときは TaskService 側で既定値を使うので、ここでは null のままでよい。
	 */
	@Column(name = "estimated_minutes")
	private Integer estimatedMinutes;

	/**
	 * 自動完了の予定時刻。予約していなければ null。
	 *
	 * 「やっちゃう」を押すと、いまの時刻に estimatedMinutes を足した時刻が入る。
	 * もう一度押すと null に戻る。
	 *
	 * 時間が来たかどうかを見るのはサーバー側なので、
	 * アプリを閉じていても完了する。画面のタイマーではこれができない。
	 *
	 * 「おまかせ中かどうか」は、この値が入っているかどうかだけで決まる。
	 * 状態を表す列を別に持つと、予定時刻との食い違いが起きる。
	 */
	@Column(name = "auto_complete_at")
	private LocalDateTime autoCompleteAt;

	// ===== メソッド（保存の前に動くもの） =====

	/**
	 * 保存される直前に、作成日時を入れる。
	 *
	 * @PrePersist が付いたメソッドは、JPA が insert の直前に呼ぶ。
	 * 呼ぶのは自分ではなく、仕組みのほう。
	 *
	 * フィールドに = LocalDateTime.now() と書くと、
	 * 「new した瞬間」の時刻が入る。保存の時刻とは別物になる。
	 *
	 * すでに値が入っているときは触らない。
	 * データを移すときなど、こちらで時刻を指定したい場合があるため。
	 */
	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	// ===== メソッド（getter / setter） =====
	// 中身を読み書きするだけ。計算や判断はここではしない。
	// ただし、値どうしが食い違うと困るものと、長さの上限だけは setter で面倒を見ている。
	//
	// 画面の ${task.title} は、実際には getTitle() を呼んでいる。
	// getter を書き忘れると、エラーは出ないのに画面が空になる。

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** 主キーを入れる。ふつうはデータベースが振るので、自分で呼ぶことはない */
	public void setId(Long id) {
		this.id = id;
	}

	/** 誰のタスクかを返す */
	public Long getUserId() {
		return userId;
	}

	/** 誰のタスクかを入れる。作成時に TaskService から呼ばれる */
	public void setUserId(Long userId) {
		this.userId = userId;
	}

	/** タスクの内容を返す。画面の ${task.title} はこれを呼んでいる */
	public String getTitle() {
		return title;
	}

	/**
	 * タスクの内容を入れる。上限を超えたら切り詰める。
	 *
	 * 画面の maxlength は開発者ツールで外せるので、守るなら受け取る側で守る。
	 * AIの提案から作るときも、ここを通る。
	 */
	public void setTitle(String title) {
		this.title = truncate(title, TITLE_MAX);
	}

	/**
	 * 難易度を返す。画面ではさらに .label を呼んで「中」などの文字にしている。
	 *
	 * null のときは中を返す。
	 * この列を足す前の行や、手でSQLを書いたときに null になりうる。
	 * null のまま .label を呼ぶと、画面全体が落ちる。
	 */
	public Difficulty getDifficulty() {
		return difficulty != null ? difficulty : Difficulty.MID;
	}

	/** 難易度を入れる */
	public void setDifficulty(Difficulty difficulty) {
		this.difficulty = difficulty;
	}

	/** 締切を返す。無ければ null */
	public LocalDate getDueDate() {
		return dueDate;
	}

	/** 締切を入れる */
	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	/** 状態を返す。null のときは未実施として扱う */
	public TaskStatus getStatus() {
		return status != null ? status : TaskStatus.TODO;
	}

	/**
	 * 状態を入れる。完了日時もここで面倒を見る。
	 *
	 * 完了になったら日時を入れ、それ以外に変わったら消す。
	 * この2つは必ずセットで動かす必要があるので、1つのメソッドにまとめてある。
	 * 別々に呼べるようにすると、「完了なのに日時が無い」行ができ、
	 * 完了一覧の並び順が狂う。
	 *
	 * すでに完了で、もう一度完了にされたときは日時を上書きしない。
	 * 最初に終わった時刻のほうが、記録として意味がある。
	 *
	 * @param status 新しい状態
	 */
	public void setStatus(TaskStatus status) {

		this.status = status;

		if (status == TaskStatus.DONE) {
			if (completedAt == null) {
				completedAt = LocalDateTime.now();
			}
		} else {
			completedAt = null;
		}
	}

	/**
	 * 表示順を返す。
	 *
	 * null のときは0を返す。
	 * 呼び出し側が int で受け取ると、null のままでは落ちる。
	 */
	public Integer getSortOrder() {
		return sortOrder != null ? sortOrder : 0;
	}

	/** 表示順を入れる。並べ替えのとき TaskService.reorder から呼ばれる */
	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

	/** 作成日時を返す */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	/** 作成日時を入れる。ふつうは onCreate が入れるので、自分で呼ぶことはない */
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * 完了日時を返す。未完了なら null。
	 *
	 * setter は用意していない。入れるのは setStatus の仕事。
	 * 状態と食い違う値が入る道を、そもそも作らない。
	 */
	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	/** やり方と終わりの線を返す。手入力のタスクでは null */
	public String getDetail() {
		return detail;
	}

	/**
	 * やり方と終わりの線を入れる。上限を超えたら切り詰める。
	 *
	 * 超えたまま保存すると、列の上限に引っかかって例外で止まる。
	 * 3案のうち1枚が長かっただけでスタート全体が失敗し、
	 * 保存はフォールバックの外なので固定3案にも切り替わらない。
	 *
	 * 切れた文章が出るほうが、画面が出ないよりましだと判断した。
	 * ただしこれは保険で、本来はプロンプト側で超えさせない（v1.6 で250字を指定）。
	 */
	public void setDetail(String detail) {
		this.detail = truncate(detail, DETAIL_MAX);
	}

	/**
	 * かかる時間の目安を返す。手入力のタスクでは null。
	 *
	 * ここでは既定値を返さない。
	 * 「目安が無い」ことと「5分」は違う意味なので、
	 * 何分にするかの判断は TaskService.startAuto に任せる。
	 */
	public Integer getEstimatedMinutes() {
		return estimatedMinutes;
	}

	/** かかる時間の目安を入れる。createFromCard でカードから引き継ぐ */
	public void setEstimatedMinutes(Integer estimatedMinutes) {
		this.estimatedMinutes = estimatedMinutes;
	}

	/** 自動完了の予定時刻を返す。予約していなければ null */
	public LocalDateTime getAutoCompleteAt() {
		return autoCompleteAt;
	}

	/** 自動完了の予定時刻を入れる。取り消すときは null を入れる */
	public void setAutoCompleteAt(LocalDateTime autoCompleteAt) {
		this.autoCompleteAt = autoCompleteAt;
	}

	// ===== メソッド（同じタスクかどうかの判定） =====

	/**
	 * 同じタスクかどうかを判定する。
	 *
	 * 比べるのは id。User は googleSub で比べたが、
	 * Task には「変わらない鍵」がタイトルにも何にも無いので、id を使う。
	 *
	 * id が null のとき（まだ保存していないとき）は false を返す。
	 * 保存前のタスクは、どれも id が無いので、
	 * 比べると全部同じものになってしまう。
	 * 同じインスタンスかどうかは、いちばん上の == で判定している。
	 */
	@Override
	public boolean equals(Object other) {

		if (this == other) {
			return true;
		}
		if (!(other instanceof Task task)) {
			return false;
		}
		return id != null && Objects.equals(id, task.id);
	}

	/**
	 * ハッシュ値を返す。
	 *
	 * 固定の値を返している。妙に見えるが、これが正しい。
	 *
	 * id から作ると、保存の前後で値が変わる（null → 1 など）。
	 * Set や Map は入れたときのハッシュ値で場所を決めるので、
	 * あとで変わると、入れたはずのものが見つからなくなる。
	 *
	 * 固定値だと、Set の中で全部が同じ場所に入るため、
	 * 件数が多いと探すのが遅くなる。ただし正しさは保たれる。
	 * 遅さは件数次第だが、間違いは件数に関係なく起きる。
	 */
	@Override
	public int hashCode() {
		return 31;
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 文字数の上限を超えていたら切り詰める。
	 *
	 * substring(0, max) は「先頭から max 文字ぶん」を取り出す書き方。
	 * 元の文字列は変わらず、切り取った新しい文字列が返る。
	 *
	 * private static にしているのは、
	 * このクラスの中だけで使い、どのタスクにも属さない処理だから。
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