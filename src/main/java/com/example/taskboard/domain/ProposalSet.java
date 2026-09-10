package com.example.taskboard.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// スタートを1回押したときの「提案1セット」を表す
// 3枚のカード（ProposalCard）がこの下にぶら下がる
@Entity
@Table(name = "proposal_sets")
public class ProposalSet {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	// 誰の提案か。ログイン機能を作るまでは固定値 1
	@Column(name="user_id")
	private Long userId;
	
	// スタートを押した時刻
	@Column(name="requested_at",nullable=false)
	private LocalDateTime requestedAt = LocalDateTime.now();
	
	// どれかを選んだ／別の話題を押した時刻。まだなら null
	// requestedAt との差が「迷い時間」になる
	@Column(name="decided_at")
	private LocalDateTime decidedAt;
	
	// どんな状況で出した提案か。今は NORMAL のみ
	@Column(length=20,nullable=false)
	private String mode="NORMAL";
	
	// この提案を誰が作ったか。MOCK / AI / FALLBACK
	@Column(length=10,nullable=false)
	private String source="MOCK";
	
	@Enumerated(EnumType.STRING)
	@Column(nullable=false,length=10)
	private ProposalStatus status = ProposalStatus.OPEN;
	
	// 実際に AI へ送ったプロンプトの全文。
	// プロンプトを試行錯誤する間、「この3案が出たときの文章」を残しておくため
	@Column(name="prompt_text",columnDefinition="TEXT")
	private String promptText;
	// --- getter / setter ---

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

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public void setRequestedAt(LocalDateTime requestedAt) {
		this.requestedAt = requestedAt;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(LocalDateTime decidedAt) {
		this.decidedAt = decidedAt;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public ProposalStatus getStatus() {
		return status;
	}

	public void setStatus(ProposalStatus status) {
		this.status = status;
	}

	public String getPromptText() {
		return promptText;
	}

	public void setPromptText(String promptText) {
		this.promptText = promptText;
	}

}
