package com.example.taskboard.domain;


//タスクの粒度。どれくらいの重さかを表す
public enum Granularity {
	
	FIVE_MIN("5分でできる"),
	TODAY("今日中にできる"),
	GROUP("まとまった時間が要る");
	
	private final String label;
	
	private Granularity(String label) {
		this.label=label;
	}
	
	public String getLabel() {
		return label;
	}
}
