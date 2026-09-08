package com.example.taskboard.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomePageController {
	
	@GetMapping("/")
	@ResponseBody
	public String hello() {
		return "Hello Task Board";
	}

}
