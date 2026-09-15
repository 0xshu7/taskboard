package com.example.taskboard.domain;

// ===== インポート =====
// 外から借りてくる部品。何のために借りているかを1行ずつ。
//
// java.time.LocalDate     … 日付だけ（2026-09-15）。締切に使う
// java.time.LocalDateTime … 日付＋時刻。作成日時・完了日時に使う
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

import java.time.LocalDate;
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
 * タスク1件。tasks テーブルの1行にあたる。
 *
 * 作られ方は2通り。
 * 1. 画面の「追加する」から手で入力する
 * 2. AIの3択から「これにする」を押す（TaskService.createFromCard）
 * 違いは detail が入っているかどうかだけ。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言。これが無いと表が作られない。
// @Table  … 対応するテーブル名。書かないとクラス名がそのまま使われる（Task → task）。

@Entity
@Table(name = "tasks")
public class Task {

	// ===== フィールド =====
	// ここに書いた項目が、そのままテーブルの列になる。

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
	@Column(nullable = false, length = 100)
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

	/** 作成日時。new した時点の時刻がそのまま入る */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	/**
	 * 完了日時。未完了なら null。
	 *
	 * 実績画面の材料はほぼこの列。
	 * 通算件数・今日の件数・週のグラフを、全部ここから数えている。
	 * 書き換えるのは TaskService.nextStatus の1か所だけにしてある。
	 */
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	/** 最後に通知した日。同じ日に2回メールを送らないための印 */
	@Column(name = "notified_date")
	private LocalDate notifiedDate;

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
	@Column(length = 300)
	private String detail;

	// ===== メソッド =====
	// 中身を読み書きするだけ。計算や判断はここではしない。
	//
	// 画面の ${task.title} は、実際には getTitle() を呼んでいる。
	// getter を書き忘れると、エラーは出ないのに画面が空になる。
	//
	// setter は this.○○ = ○○; の左右の綴りを必ず確認する。
	// 違っていると自分に自分を入れるだけになり、エラーも出ず、値も変わらない。

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

	/** タスクの内容を入れる */
	public void setTitle(String title) {
		this.title = title;
	}

	/** 難易度を返す。画面ではさらに .label を呼んで「中」などの文字にしている */
	public Difficulty getDifficulty() {
		return difficulty;
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

	/** 状態を返す */
	public TaskStatus getStatus() {
		return status;
	}

	/** 状態を入れる。完了日時とセットで扱うこと */
	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	/** 表示順を返す */
	public Integer getSortOrder() {
		return sortOrder;
	}

	/** 表示順を入れる。並べ替えのとき TaskService.reorder から呼ばれる */
	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

	/** 作成日時を返す */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	/** 作成日時を入れる。初期値が入っているので、ふつうは呼ばない */
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	/** 完了日時を返す。未完了なら null */
	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	/** 完了日時を入れる。完了以外に戻すときは null を入れる */
	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}

	/** 最後に通知した日を返す */
	public LocalDate getNotifiedDate() {
		return notifiedDate;
	}

	/** 最後に通知した日を入れる。NotificationService から呼ばれる */
	public void setNotifiedDate(LocalDate notifiedDate) {
		this.notifiedDate = notifiedDate;
	}

	/** やり方と終わりの線を返す。手入力のタスクでは null */
	public String getDetail() {
		return detail;
	}

	/** やり方と終わりの線を入れる。createFromCard でカードから引き継ぐ */
	public void setDetail(String detail) {
		this.detail = detail;
	}
}