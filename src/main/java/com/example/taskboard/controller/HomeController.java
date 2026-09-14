package com.example.taskboard.controller;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.taskboard.service.ProposalService;
import com.example.taskboard.service.TaskService;
import com.example.taskboard.service.UserService;

// アプリを開いたときの最初の画面。
// 「迷っているより、まず実行」なので、一覧ではなくスタートボタンを最初に見せる
@Controller
public class HomeController {
	
	private final TaskService taskService;
	private final ProposalService proposalService;
	private final UserService userService;

	public HomeController(TaskService taskService, ProposalService proposalService,
			UserService userService) {
		this.taskService = taskService;
		this.proposalService = proposalService;
		this.userService = userService;
	}
	
	@GetMapping("/")
	public String home(Model model) {

		model.addAttribute("remaining", taskService.countRemaining());
		model.addAttribute("volume", userService.current().getSoundVolume());
		// 裏で3案を作り始める。ここでは待たない
		proposalService.prepareInBackground();

		return "home";
	}

}
