package com.example.taskboard.domain;

//タスクの状態を表す。決まった値しか入らないので enum にする
public enum TaskStatus {
	
	TODO,  //未実施
	DOING, //進行中
	DONE   //完了

}
