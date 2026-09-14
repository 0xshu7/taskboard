package com.example.taskboard.domain;
/**
 * レベルと称号。
 * 点は「3択から選んだ＝1点」「タスクを完了した＝3点」で貯まる。
 *
 * 後ろほど間隔を広げてある。すぐ最後まで行くと、そこで終わってしまうため。
 */
public enum Level {
	
	LV1("🥚", "たまご", 0),
	LV2("🚶", "あるきだし", 10),
	LV3("🏃", "かけだし", 30),
	LV4("🔥", "ひがついた", 60),
	LV5("⛰️", "のぼりちゅう", 100),
	LV6("🌟", "つづけるひと", 160),
	LV7("👑", "さんたくの主", 250);
	
	private final String emoji;
	private final String title;
	private final int requiredPoints;
	
	Level(String emoji,String title,int requiredPoints){
		this.emoji=emoji;
		this.title=title;
		this.requiredPoints=requiredPoints;
	}

	public String getEmoji() {
		return emoji;
	}

	public String getTitle() {
		return title;
	}

	public int getRequiredPoints() {
		return requiredPoints;
	}
	
	/** 画面に出す番号。LV1 なら 1 */
	public int getNumber() {
		return ordinal()+1;
	}
	
	/** 今の点から、届いているレベルを求める */
	public static Level of(int points) {
		Level reached= LV1;
		
		// 上から順に見て、点が足りているものを覚えていく
		for (Level level:values()) {
			if (points >= level.requiredPoints) {
				reached= level;
			}
		}
		return reached;
		
	}
	
	/** 次のレベル。もう最後なら null */
	public Level next() {
		int index = ordinal()+1;
		
		if(index>=values().length) {
			return null;
		}
		return values()[index];
	}

}
