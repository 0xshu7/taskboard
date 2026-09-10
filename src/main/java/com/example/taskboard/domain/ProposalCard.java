package com.example.taskboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 提案カード1枚。1セットにつき3枚できる
@Entity
@Table(name = "proposal_cards")
public class ProposalCard {
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long id;
	
	// どのセットのカードか
	@Column(name="proposal_set_id",nullable=false)
	private Long proposalSetId;
	
	// 何番目のカードか。1〜3
	@Column(nullable=false)
	private Integer position;
	
	@Column(nullable=false,length=100)
	private String title;
	
	// 生活 / 勉強 / 食事 / 休日 など
	@Column(length=30)
	private String category;
	
	@Enumerated(EnumType.STRING)
	@Column(length=10)
	private Granularity granularity;
	
	@Enumerated(EnumType.STRING)
	@Column(length=10)
	private Difficulty difficulty;
	
	@Column(name="estimated_minutes")
	private Integer estimatedMinutes;
	
	// なぜこれを勧めるのかの一言。ここが「その人向け」に見えるかどうかの要
	@Column(length=200)
	private String reason;
	
	// このカードが選ばれたか
	@Column(nullable=false)
	private boolean chosen=false;
	// --- getter / setter ---

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProposalSetId() {
		return proposalSetId;
	}

	public void setProposalSetId(Long proposalSetId) {
		this.proposalSetId = proposalSetId;
	}

	public Integer getPosition() {
		return position;
	}

	public void setPosition(Integer position) {
		this.position = position;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Granularity getGranularity() {
		return granularity;
	}

	public void setGranularity(Granularity granularity) {
		this.granularity = granularity;
	}

	public Difficulty getDifficulty() {
		return difficulty;
	}

	public void setDifficulty(Difficulty difficulty) {
		this.difficulty = difficulty;
	}

	public Integer getEstimatedMinutes() {
		return estimatedMinutes;
	}

	public void setEstimatedMinutes(Integer estimatedMinutes) {
		this.estimatedMinutes = estimatedMinutes;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public boolean isChosen() {
		return chosen;
	}

	public void setChosen(boolean chosen) {
		this.chosen = chosen;
	}

}
