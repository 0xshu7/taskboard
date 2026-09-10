package com.example.taskboard.logic;

import com.example.taskboard.domain.Granularity;

//「今どんな状況か」をまとめて持つ入れ物。
//SituationJudge が作り、PromptBuilder が読む

public class Situation {
	
	// 朝 / 昼 / 夜 / 深夜
	private final String timeOfDay;
	
	// この時間帯に勧めたい重さ
	private final Granularity recommended;
	
	// AI に伝える一言。「夜なので軽いものを」など
	private final String note;
	
	public Situation(String timeOfDay, Granularity recommended,String note) {
		this.timeOfDay=timeOfDay;
		this.recommended=recommended;
		this.note=note;
	}
	// final でsetterなし
	public String getTimeOfDay() {return timeOfDay; }
	public Granularity getReGranularity() {return recommended;}
	public String getNote() {return note;}
}