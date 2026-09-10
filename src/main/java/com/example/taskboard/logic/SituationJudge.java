package com.example.taskboard.logic;

import java.time.LocalTime;

import org.springframework.stereotype.Component;

import com.example.taskboard.domain.Granularity;

// 今の時刻から「どんな提案が向いているか」を決める。
// AI に丸投げせず、アプリ側で判断する部分。
// 純粋な Java なので、単体テストが書きやすい
@Component
public class SituationJudge {
	
	public Situation judge(LocalTime now) {
		
		int hour=now.getHour();
		
		// 朝：これから1日が始まる。軽いもので勢いをつける
		if(hour >= 5 && hour <11) {
			return new Situation("朝",Granularity.FIVE_MIN,
					"朝なので、短時間で終わって勢いがつくものがよい");
		}
		
		// 昼：まとまった時間が取りやすい
		if (hour >= 11 && hour < 17) {
			return new Situation("昼",Granularity.TODAY,
					"日中なので、今日中に終えられる程度のものがよい");
		}
		
		// 夜：疲れている。重いものは続かない
		if(hour >= 17 && hour < 22) {
			return new Situation("夜", Granularity.FIVE_MIN,
					"夜なので、疲れていても手が動く軽いものがよい");
		}
		
		// 深夜：明日に響かないものだけ
		return new Situation("深夜",Granularity.FIVE_MIN,
				"深夜なので、明日に響かない5分程度のものだけにする");
	}

}
