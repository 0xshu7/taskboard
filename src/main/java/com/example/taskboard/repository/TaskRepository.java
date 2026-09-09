package com.example.taskboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {

	// 状態に関係なく、その人のタスクを並び順で全部取る
	List<Task> findByUserIdOrderBySortOrderAsc(Long userId);

	// 「userId が一致し、status が DONE でない行の件数」＝ 残りタスク数
	long countByUserIdAndStatusNot(Long userId, TaskStatus status);
}