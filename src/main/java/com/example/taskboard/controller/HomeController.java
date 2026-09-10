package com.example.taskboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.taskboard.service.TaskService;

// アプリを開いたときの最初の画面。
// 「迷っているより、まず実行」なので、一覧ではなくスタートボタンを最初に見せる
@Controller
public class HomeController {
	
	private final TaskService taskService;
	
	public HomeController(TaskService taskService) {
		this.taskService=taskService;
		
	}
	
	@GetMapping("/")
	public String home(Model model) {
		
		// 残りタスク数だけ渡す。一覧は出さない
		model.addAttribute("remaining",taskService.countRemaining());
		
		return "home";
	}

}
