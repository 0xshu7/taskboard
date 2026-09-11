package com.example.taskboard.controller;

import java.time.LocalTime;

import com.example.taskboard.domain.User;

/**
 * 設定画面の入れ物。
 * 画面の入力欄と、このクラスの項目が1対1で対応する。
 *
 * 【項目を増やすとき】
 *   1. User に列を足す
 *   2. このクラスに項目を足す（from と apply にも1行ずつ）
 *   3. settings.html に入力欄を1つ足す
 *   コントローラとサービスは触らなくてよい。
 */
public class SettingsForm {
	
	private String displayName;
	
	private boolean notifyEnabled;
	
	/**
	 * 時刻は文字のまま受け取る。
	 * LocalTime で受けると、空欄のときに変換エラーで落ちるため。
	 */
	private String notifyTime;
	
	private String notifyEmail;
	
	/** いまの設定を、画面用に写す */
	public static SettingsForm from(User user) {
		SettingsForm form = new SettingsForm();
		
		form.setDisplayName(user.getDisplayName());
		form.setNotifyEnabled(user.isNotifyEnabled());
		form.setNotifyTime(user.getNotifyTime()==null ? "" :user.getNotifyTime().toString());
		form.setNotifyEmail(user.getNotifyEmail());
		
		return form;
		
	}
	
	/** 画面で入力された値を、User に書き戻す */
	public void apply(User user) {
		user.setDisplayName(displayName);
		user.setNotifyEnabled(notifyEnabled);
		user.setNotifyTime(toTime(notifyTime));
		user.setNotifyEmail(notifyEmail);
		
	}
	
	/** "08:00" を LocalTime に直す。空なら null（通知しない、の意味） */
	private LocalTime toTime(String text) {
		if(text==null||text.isBlank()) {
			return null;
			
		}
		
		return LocalTime.parse(text);
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	
	public void setDisplayName(String displayName) {
		this.displayName=displayName;
	}
	
	/** boolean の getter は get ではなく is で始める。ここを間違えると画面で値が読めない */
	public boolean isNotifyEnabled() {
		return notifyEnabled;
	}

	public void setNotifyEnabled(boolean notifyEnabled) {
		this.notifyEnabled = notifyEnabled;
	}

	public String getNotifyTime() {
		return notifyTime;
	}

	public void setNotifyTime(String notifyTime) {
		this.notifyTime = notifyTime;
	}

	public String getNotifyEmail() {
		return notifyEmail;
	}

	public void setNotifyEmail(String notifyEmail) {
		this.notifyEmail = notifyEmail;
	}
}

