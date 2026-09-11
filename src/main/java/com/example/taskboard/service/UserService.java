package com.example.taskboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.controller.SettingsForm;
import com.example.taskboard.domain.User;
import com.example.taskboard.repository.UserRepository;

/**
 * いまの利用者を取ってくる係。
 * ログインが無いので「1人目」を返すだけ。まだ誰もいなければ作る。
 */
@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository =userRepository;
		
		
	}
	
	/** いまの利用者。無ければ作って返す */
	@Transactional
	public User current() {
		return userRepository.findById(TaskService.USER_ID)
				.orElseGet(this::createDefault);
		
		
	}
	
	/** 最初の1人を作る。ログインを入れたら、このメソッドは要らなくなる */
	private User createDefault() {
		// User には (email, displayName) を受け取るコンストラクタがあるので、それを使う
		User user = new User("local@example.com","あなた");
		return userRepository.save(user);
		
		
	}
	
	/** 設定の保存。項目が増えても、このメソッドは変わらない */
	@Transactional
	public void updateSettings(SettingsForm form) {
		User user =current();
		form.apply(user);
		userRepository.save(user);
	}

}
