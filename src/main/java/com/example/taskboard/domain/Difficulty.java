package com.example.taskboard.domain;

//難易度を表す。画面には「低・中・高」と出したいので、日本語のラベルも持たせる
public enum Difficulty {

	LOW("低"),   // かっこの中がラベル。下のコンストラクタに渡される
	MID("中"),
	HIGH("高");
	
	// 各定数が持つ日本語ラベル。1度決めたら変わらないので final
	private final String label;
	
	// enum のコンストラクタ。上の LOW("低") が呼ばれたときに動く
	private Difficulty(String label) {
		this.label=label;
	}

	
	
	// 画面から取り出すための getter。Thymeleaf で ${task.difficulty.label} と書ける
	public String getLabel() {
		return label;
	}
	
}
