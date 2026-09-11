package com.example.taskboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync        // ← 追加。裏で処理を走らせる機能を有効にする
@SpringBootApplication
@EnableScheduling      // 決まった時刻に処理を動かす機能を有効にする
public class TaskboardApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskboardApplication.class, args);
	}

}
