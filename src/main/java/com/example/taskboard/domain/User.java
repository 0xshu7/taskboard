package com.example.taskboard.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="users")
public class User {
	
	@Id
	@GeneratedValue(strategy= GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false,unique=true,length=255)
	private String email;
	
	@Column(name="password_hash",length=255)
	private String passwordHash;
	
	@Column(name = "google_sub",unique=true,length=64)
	private String googleSub;
	
	@Column(name = "display_name",nullable=false,length=50)
	private String displayName;
	
	@Column(nullable=false)
	private boolean onboarded = false;
	
	@Column(name = "created_at",nullable=false)
	private LocalDateTime createdAt = LocalDateTime.now();
	
	/** 通知を送るかどうか */
	@Column(name = "notify_enabled")
	private boolean notifyEnabled=false;
	
	/** 通知を送る時刻。通知しないときは null */
	@Column(name="notify_time")
	private LocalTime notifyTime;
	
	/** 通知先。空なら email に送る */
	@Column(name="notify_email",length=255)
	private String notifyEmail;
	
	/** 最後に通知した日。同じ日に2回送らないための印 */
	@Column(name="last_notified_date")
	private LocalDate lastNotifiedDate;
	
	
	public boolean isNotifyEnabled() {
		return notifyEnabled;
	}

	public void setNotifyEnabled(boolean notifyEnabled) {
		this.notifyEnabled = notifyEnabled;
	}

	public LocalTime getNotifyTime() {
		return notifyTime;
	}

	public void setNotifyTime(LocalTime notifyTime) {
		this.notifyTime = notifyTime;
	}

	public String getNotifyEmail() {
		return notifyEmail;
	}

	public void setNotifyEmail(String notifyEmail) {
		this.notifyEmail = notifyEmail;
	}

	public LocalDate getLastNotifiedDate() {
		return lastNotifiedDate;
	}

	public void setLastNotifiedDate(LocalDate lastNotifiedDate) {
		this.lastNotifiedDate = lastNotifiedDate;
	}
	
	/** 表示名は設定画面で変えられるので setter が要る */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void setOnboarded(boolean onboarded) {
		this.onboarded = onboarded;
	}

	protected User() {	}
	
	public User(String email,String displayName) {
		this.email=email;
		this.displayName=displayName;
	}

	
	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean isOnboarded() {
		return onboarded;
	}

}
