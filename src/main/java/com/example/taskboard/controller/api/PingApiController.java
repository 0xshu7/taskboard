package com.example.taskboard.controller.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskboard.ai.GeminiClient;

@RestController
public class PingApiController {
	
	private final GeminiClient gemini;
	
	public PingApiController(GeminiClient gemini) {
		this.gemini=gemini;
	}
	
	@GetMapping(value= "/api/ping-ai",produces="application/json")
	public String pingAi() {
		return gemini.generate("PROPOSAL", "ping");
		
	}

}
