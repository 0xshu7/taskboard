package com.example.taskboard.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.taskboard.domain.Level;
import com.example.taskboard.domain.ProposalStatus;
import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.repository.ProposalSetRepository;
import com.example.taskboard.repository.TaskRepository;

/**
 * 実績画面のための集計。
 * 新しいテーブルは作らず、いまあるデータから数えているだけ。
 */
@Service
public class RecordService {

	/** 3択から選んだときに入る点 */
	private static final int POINT_CHOSEN = 1;

	/** タスクを完了したときに入る点 */
	private static final int POINT_DONE = 3;

	private final TaskRepository taskRepository;
	private final ProposalSetRepository proposalSetRepository;

	public RecordService(TaskRepository taskRepository, ProposalSetRepository proposalSetRepository) {
		this.taskRepository = taskRepository;
		this.proposalSetRepository = proposalSetRepository;
	}

	/** 通算で完了した件数 */
	public long countDone() {
		return taskRepository.countByUserIdAndStatus(TaskService.USER_ID, TaskStatus.DONE);
	}

	/** 通算で3択から選んだ回数 */
	public long countChosen() {
		return proposalSetRepository.countByUserIdAndStatus(TaskService.USER_ID, ProposalStatus.CHOSEN);
	}

	/** いまの点 */
	public int points() {
		return (int) (countChosen() * POINT_CHOSEN + countDone() * POINT_DONE);
	}

	/** いまのレベル */
	public Level level() {
		return Level.of(points());
	}

	/** 次のレベルまで、あと何回完了すればよいか。最後まで行っていれば null */
	public Integer remainingToNext() {
		Level next = level().next();

		if (next == null) {
			return null;
		}

		int shortage = next.getRequiredPoints() - points();

		// 完了1回で3点入るので、3で割って切り上げる
		return (int) Math.ceil(shortage / (double) POINT_DONE);
	}

	/** 全部の称号。画面で「達成したものだけ見せる」ために使う */
	public Level[] allLevels() {
		return Level.values();
	}

	/** この7日間の、1日ごとの完了件数 */
	public List<DayCount> lastSevenDays() {

		LocalDate today = LocalDate.now();
		LocalDate from = today.minusDays(6);      // 今日を含めて7日

		// 7日ぶんの完了タスクを取ってくる
		List<Task> done = taskRepository.findByUserIdAndStatusAndCompletedAtAfter(
				TaskService.USER_ID, TaskStatus.DONE, from.atStartOfDay());

		// 日付ごとに数える
		Map<LocalDate, Long> countByDate = new HashMap<>();
		for (Task task : done) {
			LocalDate date = task.getCompletedAt().toLocalDate();
			countByDate.put(date, countByDate.getOrDefault(date, 0L) + 1);
		}

		// 一番多い日を探す。棒の長さをこれとの比で決める
		long max = 0;
		for (long count : countByDate.values()) {
			if (count > max) {
				max = count;
			}
		}

		// 古い日から順に7本ぶん作る
		List<DayCount> days = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			LocalDate date = from.plusDays(i);
			long count = countByDate.getOrDefault(date, 0L);

			// 曜日を「月」「火」のような1文字にする
			String label = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.JAPANESE);

			// 棒の長さ（0〜100）。1件でも入っていたら見えるように、最低8にしておく
			int percent = 0;
			if (max > 0 && count > 0) {
				percent = Math.max(8, (int) (count * 100 / max));
			}

			days.add(new DayCount(label, count, percent, date.equals(today)));
		}
		return days;
	}

	/**
	 * グラフの1日ぶん。
	 * 画面に渡すためだけの入れ物なので、このファイルの中に置いている。
	 */
	public static class DayCount {

		private final String label;
		private final long count;
		private final int percent;
		private final boolean today;

		public DayCount(String label, long count, int percent, boolean today) {
			this.label = label;
			this.count = count;
			this.percent = percent;
			this.today = today;
		}

		public String getLabel() {
			return label;
		}

		public long getCount() {
			return count;
		}

		public int getPercent() {
			return percent;
		}

		public boolean isToday() {
			return today;
		}
	}
}