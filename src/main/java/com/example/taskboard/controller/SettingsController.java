package com.example.taskboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.taskboard.dto.SettingsForm;
import com.example.taskboard.service.UserService;

@Controller
public class SettingsController {

	
	private final UserService userService;
	
	public SettingsController(UserService userService) {
		this.userService=userService;
	}
	
	/** 設定画面を出す */
	@GetMapping("/settings")
	public String settings(Model model) {
		model.addAttribute("form",SettingsForm.from(userService.current()));
		return "settings";
	}
	
	/**
	 * 設定を保存する。
	 * @ModelAttribute が、画面の入力欄と SettingsForm の項目を自動で結びつける。
	 * だから項目が増えても、このメソッドは1文字も変わらない。
	 */
	@PostMapping("/settings")
	public String save(@ModelAttribute("form")SettingsForm form) {
		userService.updateSettings(form);
		return "redirect:/settings";
	}
}
