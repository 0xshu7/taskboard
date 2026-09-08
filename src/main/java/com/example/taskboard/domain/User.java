package com.example.taskboard.domain;

import java.time.LocalDateTime;

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
