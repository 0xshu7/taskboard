package com.example.taskboard.repository;

// ===== インポート =====
// java.time.LocalDate     … 日付だけ。締切の範囲指定に使う
// java.time.LocalDateTime … 日付＋時刻。自動完了の予定時刻の比較に使う
// java.util.List          … リストの型。複数件を受け取るのに使う
//
// 以下は Spring Data JPA の部品。
// JpaRepository … 保存・取得・削除の基本的な処理をまとめて用意してくれる
//
// 以下はこのアプリの部品。
// Task       … タスク1件
// TaskStatus … タスクの状態

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;

/**
 * タスクの出し入れ。
 *
 * クラスではなくインターフェース。中身は1行も書かない。
 * Spring が起動時にメソッド名を読み取り、対応する SQL を自動で組み立てる。
 *
 * 名前の付け方の規則は次のとおり。
 *
 *   findBy / countBy   … 取り出すか、数えるか
 *   And                … 条件をつなぐ
 *   Not                … 「〜でない」（SQL の <>）
 *   Between            … 範囲。両端を含む
 *   LessThanEqual      … 以下
 *   OrderBy…Asc / Desc … 並び順
 *
 * 条件に書く項目名は、Task のフィールド名と一致させる。
 * 列名（due_date）ではなくフィールド名（dueDate）で書く。
 *
 * 名前を間違えてもコンパイルは通る。起動したときに初めて失敗する。
 * Java から見れば「中身の無いメソッド」でしかないため、
 * 正しいかどうかはアプリを動かすまで分からない。
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

	// ===== メソッド（一覧を取り出す） =====

	/**
	 * 完了していないタスク（未実施・進行中）を表示順で取る。
	 *
	 * SELECT * FROM tasks WHERE user_id = ? AND status &lt;&gt; ? ORDER BY sort_order ASC
	 *
	 * Not が付くので、渡した状態「以外」が対象になる。
	 * DONE を渡すことで、未実施と進行中の両方が取れる。
	 * TODO と DOING を別々に取って足し合わせるより、条件が1つで済む。
	 *
	 * @param userId 利用者のID
	 * @param status 除外する状態。DONE を渡す
	 * @return 表示順に並んだ未完了のタスク
	 */
	List<Task> findByUserIdAndStatusNotOrderBySortOrderAsc(Long userId, TaskStatus status);

	/**
	 * 完了したタスクを、完了が新しい順で取る。
	 *
	 * SELECT * FROM tasks WHERE user_id = ? AND status = ? ORDER BY completed_at DESC
	 *
	 * こちらは Not が付かないので、渡した状態そのものが対象になる。
	 * 並びも表示順ではなく完了日時の降順にしている。
	 * やり終えた順に見えるほうが、記録として読みやすいため。
	 *
	 * @param userId 利用者のID
	 * @param status 対象の状態。DONE を渡す
	 * @return 完了が新しい順に並んだタスク
	 */
	List<Task> findByUserIdAndStatusOrderByCompletedAtDesc(Long userId, TaskStatus status);

	/**
	 * 締切が指定した期間内で、まだ完了していないタスクを、締切の近い順で取る。
	 *
	 * SELECT * FROM tasks
	 * WHERE user_id = ? AND status &lt;&gt; ? AND due_date BETWEEN ? AND ?
	 * ORDER BY due_date ASC
	 *
	 * Between は両端を含む。今日と明日を渡すと、どちらの日も対象になる。
	 * 通知の対象を選ぶために使う。
	 *
	 * @param userId 利用者のID
	 * @param status 除外する状態。DONE を渡す
	 * @param from   範囲の始まり。この日を含む
	 * @param to     範囲の終わり。この日を含む
	 * @return 締切の近い順に並んだタスク
	 */
	List<Task> findByUserIdAndStatusNotAndDueDateBetweenOrderByDueDateAsc(
			Long userId, TaskStatus status, LocalDate from, LocalDate to);

	/**
	 * 自動完了の時刻が来た進行中のタスクを探す。
	 *
	 * SELECT * FROM tasks WHERE status = ? AND auto_complete_at &lt;= ?
	 *
	 * LessThanEqual は「以下」。いまの時刻を渡すと、予定時刻を過ぎたものが返る。
	 *
	 * autoCompleteAt が null の行は、この条件では返らない。
	 * 予約していないタスクが勝手に完了することはない。
	 *
	 * 利用者で絞っていない。
	 * 10秒ごとに時計から呼ばれるので、そのときログインしている人がいないため。
	 * 全員ぶんをまとめて取り、タスクが持つ userId から送り先を引く。
	 *
	 * @param status 対象の状態。DOING を渡す
	 * @param time   この時刻以前に予約されたものを対象にする。いまの時刻を渡す
	 * @return 完了にするべきタスク。無ければ空のリスト
	 */
	List<Task> findByStatusAndAutoCompleteAtLessThanEqual(TaskStatus status, LocalDateTime time);

	// ===== メソッド（数える） =====

	/**
	 * 完了していないタスクの件数を数える。画面の「残り○件」に使う。
	 *
	 * SELECT COUNT(*) FROM tasks WHERE user_id = ? AND status &lt;&gt; ?
	 *
	 * 一覧を取り出して数えることもできるが、
	 * 件数だけが欲しいときは数える側で済ませたほうが無駄がない。
	 *
	 * @param userId 利用者のID
	 * @param status 除外する状態。DONE を渡す
	 * @return 未完了のタスク数
	 */
	long countByUserIdAndStatusNot(Long userId, TaskStatus status);
}