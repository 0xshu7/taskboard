package com.example.taskboard.ai;

public interface GeminiClient {
    /** purpose: PROPOSAL / INTERVIEW / MEMO_UPDATE / NOTFIT / NOTIFICATION */
	String generate(String purpose,String prompt);
	
}
