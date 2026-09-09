package com.example.taskboard.domain;

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

//このクラス1つが tasks テーブルの1行に対応する
@Entity
@Table(name="tasks")
public class Task {
	
	// 主キー。値はデータベースが自動で採番する
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long id;
	
	// 誰のタスクか。ログイン機能を作るまでは固定値 1 を入れる
	@Column(name="user_id")
	private Long userId;
	
	// タスクの内容。空は許さず、100文字まで
	@Column(nullable=false,length=100)
	private String title;
	
	// 難易度。EnumType.STRING を付けると "LOW" という文字列で保存される
    // 付け忘れると 0,1,2 という数字で保存され、あとで順番を変えたときに意味がずれる
	@Enumerated(EnumType.STRING)
	@Column(length=10)
	private Difficulty difficulty=Difficulty.MID;
	
	// 締切。設定しないタスクもあるので null を許す（画面では非表示にする）
	@Column(name="due_date")
	private LocalDate dueDate;
	
	// 状態。新規作成時は未実施から始まる
	@Enumerated(EnumType.STRING)
	@Column(nullable=false,length=10)
	private TaskStatus status = TaskStatus.TODO;
	
	// 表示順。数が小さいほど上に出す。上下移動でこの値を隣と入れ替える
	@Column(name="sort_order",nullable=false)
	private Integer sortOrder=0;
	
	// 作成日時。new した時点の時刻を入れておく
	@Column(name= "created_at",nullable=false)
	private LocalDateTime createdAt=LocalDateTime.now();
	
	// 完了日時。完了していなければ null。あとで「連続日数」を数えるときに使う
	@Column(name="completed_at")
	private LocalDateTime completedAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Difficulty getDifficulty() {
		return difficulty;
	}

	public void setDifficulty(Difficulty difficulty) {
		this.difficulty = difficulty;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}

}
