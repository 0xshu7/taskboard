package com.example.taskboard.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {

	
	// 締切が指定した期間内で、まだ完了していないタスクを締切の近い順に取る
	// Between は「以上・以下」（両端を含む）
	List<Task> findByUserIdAndStatusNotAndDueDateBetweenOrderByDueDateAsc(
			Long userId, TaskStatus status, LocalDate from, LocalDate to);
	
	
	// 状態に関係なく、その人のタスクを並び順で全部取る
	List<Task> findByUserIdOrderBySortOrderAsc(Long userId);

	// 「userId が一致し、status が DONE でない行の件数」＝ 残りタスク数
	long countByUserIdAndStatusNot(Long userId, TaskStatus status);
	
	// 完了していないタスク（未実施・進行中）を並び順で
	List<Task> findByUserIdAndStatusNotOrderBySortOrderAsc(Long userId,TaskStatus status);
	
	// 完了したタスクを、完了が新しい順で
	List<Task>findByUserIdAndStatusOrderByCompletedAtDesc(Long userId,TaskStatus status);
}