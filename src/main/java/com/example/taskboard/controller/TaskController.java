package com.example.taskboard.controller;

// ===== インポート =====
// java.time.LocalDate … 日付だけ。締切の受け取りと、今日の日付に使う
// java.util.List      … リストの型。並べ替えのID一覧に使う
//
// 以下は Spring の部品。
// DateTimeFormat … 画面から来た文字を日付に変換する形式を指定する
// Controller     … HTMLを返すクラスです、という印
// Model          … 画面へ値を渡すための入れ物
// GetMapping     … そのURLを開いたときに動くメソッドの印
// PathVariable   … URLの一部（/tasks/5/update の 5）を受け取る
// PostMapping    … そのURLへ送信されたときに動くメソッドの印
// RequestParam   … 入力欄の値を受け取る
// ExceptionHandler … このクラスの中で起きた例外を受け止めるメソッドの印
//
// MethodArgumentTypeMismatchException
//   … 画面から来た文字を引数の型に変換できなかったときの例外
//
// 以下はこのアプリの部品。
// Difficulty   … 難易度
// SettingsForm … 画面へ渡す設定の入れ物
// TaskService  … タスクの処理
// UserService  … 利用者と設定

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.dto.SettingsForm;
import com.example.taskboard.service.TaskService;
import com.example.taskboard.service.UserService;

/**
 * タスク一覧を受け持つ。
 *
 * 扱うURLは2種類。
 * 表示（GET）  … /tasks
 * 操作（POST） … 追加・更新・状態変更・並べ替え・削除・自動完了の予約
 *
 * 操作のあとは必ず redirect: で表示のURLへ移す。
 * そのまま画面を返すと、ブラウザで再読み込みしたときに
 * 同じ操作がもう一度送られ、タスクが二重にできる。
 *
 * 判断や計算はここには書かない。すべて TaskService に任せている。
 * このクラスがしているのは、URLと処理を結びつけ、結果の行き先を決めることだけ。
 *
 * ただし「そもそも処理を始めてよいか」の確認だけはここで行う。
 * 空のタイトルのように、service を呼ぶ意味すら無いものを手前で止めている。
 *
 * 他人のタスクかどうかの確認は TaskService.findById が行う。
 * URLの番号を書き換えても、他人のものには届かない。
 */

// ===== アノテーション =====
// @Controller … HTMLを返すクラスです、という印。
//                返した文字列（"tasks"）は templates/tasks.html を指す。
//                @RestController にすると、その文字列がそのまま画面に出てしまう。

@Controller
public class TaskController {

	// ===== フィールド =====

	/**
	 * 題名の長さの上限。
	 *
	 * Task の列の長さと同じ値にしてある。
	 * 画面の入力欄にも maxlength で同じ値を入れてある。
	 */
	private static final int TITLE_MAX = 100;

	/** 完了のヒントの長さの上限。Task の列の長さと同じ値にしてある */
	private static final int DETAIL_MAX = 300;

	/** タスクの処理 */
	private final TaskService taskService;

	/** 利用者と設定。効果音と紙ふぶきの入切を画面へ渡すために使う */
	private final UserService userService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 *
	 * ここで受け取ったものを使うだけで、自分では new しない。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public TaskController(TaskService taskService, UserService userService) {
		this.taskService = taskService;
		this.userService = userService;
	}

	// ===== メソッド（表示） =====

	/**
	 * タスク一覧を表示する。
	 *
	 * 未完了と完了を別々に渡しているが、未完了は1つのリストにまとめてある。
	 * 未実施と進行中で分けるとリストが増えるだけで、状態はタグの色で見分けられる。
	 *
	 * 今日の日付を渡しているのは、締切を過ぎたタスクを色分けするため。
	 * 画面側では LocalDate.now() のような処理を書けないので、ここで用意する。
	 *
	 * error は、操作が止まったときに付けて戻ってくる印。
	 * 文面そのものを URL に乗せず、合言葉だけを渡している。
	 * 文面を URL に書くと、他の人が作ったリンクで好きな文を出せてしまう。
	 *
	 * @param error 止まった理由の合言葉。止まっていなければ null
	 * @param model 画面へ値を渡す入れ物
	 * @return templates/tasks.html
	 */
	@GetMapping("/tasks")
	public String index(@RequestParam(required = false) String error, Model model) {

		// 操作が止まったときの説明。止まらなかったときは null で、画面にも出ない
		model.addAttribute("errorMessage", messageFor(error));

		model.addAttribute("tasks", taskService.findActive());
		model.addAttribute("doneTasks", taskService.findDone());
		model.addAttribute("remaining", taskService.countRemaining());

		// 効果音と紙ふぶきの入切。画面の body に渡し、JavaScript がそこから読む。
		// 設定は入れ物ごと渡す。項目が増えても、この行は変わらない
		model.addAttribute("settings", SettingsForm.from(userService.current()));

		// 難易度の選択肢。列挙型の一覧をそのまま渡す
		model.addAttribute("difficulties", Difficulty.values());

		// 締切超過の判定に使う
		model.addAttribute("today", LocalDate.now());

		return "tasks";
	}

	// ===== メソッド（操作） =====

	/**
	 * 新しいタスクを追加する。
	 *
	 * 難易度と締切は required = false にしている。
	 * 難易度を選ばずに送ると null が届き、service 側で「中」になる。
	 * 必須にすると、追加のたびに判断が1つ増える。
	 *
	 * @DateTimeFormat は、画面から来た "2026-09-18" という文字を
	 * LocalDate に変換するための指定。形式を書かないと変換できずに失敗する。
	 *
	 * タイトルが空のときは、何もせずに一覧へ戻す。
	 * 画面の required で止めているが、開発者ツールで外せば送れてしまう。
	 * 空白だけのタスクができると、消すまで一覧に残り続ける。
	 *
	 * タイトルについてはエラーの表示を出さない。
	 * 画面の required で防いでいるものを、わざわざ外した人に説明する必要がない。
	 *
	 * 締切は違う。日付を選ぶ画面からでも、過ぎた日を選べてしまう。
	 * 選べるのに静かに無視されると、入ったつもりで入っていない状態になる。
	 *
	 * @param title      タスクの内容
	 * @param difficulty 難易度。未選択なら null
	 * @param dueDate    締切。未入力なら null
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks")
	public String create(
			@RequestParam String title,
			@RequestParam(required = false) Difficulty difficulty,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {

		if (isBlank(title)) {
			return "redirect:/tasks";
		}

		// 過ぎた日付を締切にして追加することはできない。
		// 年を打ち間違えたときに、1年前の締切が静かに入るのを防ぐ。
		//
		// 書き換えのほうでは止めない。
		// 締切を過ぎたタスクは「超過」として画面に出す作りなので、
		// ここで止めると、超過したタスクの題名を直せなくなる。
		if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
			return "redirect:/tasks?error=duePast";
		}

		if (tooLong(title, TITLE_MAX)) {
			return "redirect:/tasks?error=titleLong";
		}

		taskService.create(title, difficulty, dueDate);
		return "redirect:/tasks";
	}

	/**
	 * 既存のタスクを書き換える。
	 *
	 * @PathVariable は URL の一部を受け取る。
	 * /tasks/5/update なら、{id} の部分にあたる 5 が入る。
	 *
	 * detail（完了のヒント）は空欄で保存されることがあるので required = false にする。
	 *
	 * タイトルが空のときは何もしない。
	 * ここで通してしまうと、既にあるタスクのタイトルが消える。
	 * 追加のときより実害が大きい。
	 *
	 * @param id         タスクのID
	 * @param title      新しい内容
	 * @param difficulty 新しい難易度
	 * @param dueDate    新しい締切
	 * @param detail     新しい完了のヒント
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/update")
	public String update(@PathVariable Long id,
			@RequestParam String title,
			@RequestParam Difficulty difficulty,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
			@RequestParam(required = false) String detail) {

		if (isBlank(title)) {
			return "redirect:/tasks";
		}

		if (tooLong(title, TITLE_MAX)) {
			return "redirect:/tasks?error=titleLong";
		}

		// 完了のヒントも長さを見る。
		// Task.setDetail が300文字で切り詰めるので保存は通るが、
		// 本人には何も伝わらないまま文章が切れることになる
		if (tooLong(detail, DETAIL_MAX)) {
			return "redirect:/tasks?error=detailLong";
		}

		taskService.update(id, title, difficulty, dueDate, detail);
		return "redirect:/tasks";
	}

	/**
	 * 状態を1つ進める。タグを押したときに呼ばれる。
	 *
	 * 未実施 → 進行中 → 完了 → 未実施 と一周する。
	 *
	 * @param id タスクのID
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/next")
	public String next(@PathVariable Long id) {
		taskService.nextStatus(id);
		return "redirect:/tasks";
	}

	/**
	 * ドラッグで並べ替えた結果を保存する。
	 *
	 * ids には "3,1,2" のようにIDがカンマ区切りで届く。
	 * Spring がカンマで区切って List&lt;Long&gt; に変換してくれるので、
	 * 自分で分解する必要はない。
	 *
	 * @param ids 上から順に並んだタスクIDの一覧
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/reorder")
	public String reorder(@RequestParam List<Long> ids) {
		taskService.reorder(ids);
		return "redirect:/tasks";
	}

	/**
	 * タスクを削除する。
	 *
	 * @param id タスクのID
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/delete")
	public String delete(@PathVariable Long id) {
		taskService.delete(id);
		return "redirect:/tasks";
	}

	/**
	 * 「やっちゃう」を押したとき。自動完了を予約する。
	 *
	 * @param id 予約するタスクのID
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/auto")
	public String startAuto(@PathVariable Long id) {
		taskService.startAuto(id);
		return "redirect:/tasks";
	}

	/**
	 * 「おまかせ中」をもう一度押したとき。予約を取り消す。
	 *
	 * @param id 取り消すタスクのID
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/auto/cancel")
	public String cancelAuto(@PathVariable Long id) {
		taskService.cancelAuto(id);
		return "redirect:/tasks";
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 中身が無い文字かどうかを判定する。
	 *
	 * isBlank は「空文字か、空白だけか」を見る。
	 * isEmpty だと、空白1文字が通ってしまう。
	 *
	 * null も一緒に見ているので、呼ぶ側で2つ書かずに済む。
	 *
	 * @param text 判定する文字
	 * @return 中身が無ければ true
	 */
	private boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	/**
	 * 題名が上限を超えているかを判定する。
	 *
	 * 超えていたら、ここで止めて知らせる。
	 * Task の setter は、入れる直前に同じ長さで切り詰める。
	 * そちらは AI が作った題名を受けるときの備えとして残してある。
	 *
	 * 本人が打った文字を黙って切ると、入れたつもりのものが入らない。
	 * 画面の maxlength で止まる作りにはしてあるが、
	 * 外して送られたときにも、何が起きたかが分かるようにしておく。
	 *
	 * 数え方は Task の切り詰めと同じで、コードポイントで数える。
	 * length() だと絵文字が2文字ぶんになり、片方だけ基準がずれる。
	 *
	 * @param text 調べる文字列
	 * @param max  上限の文字数
	 * @return 上限を超えていれば true
	 */
	private boolean tooLong(String text, int max) {
		return text != null && text.codePointCount(0, text.length()) > max;
	}

	/**
	 * 合言葉を、画面に出す文に変える。
	 *
	 * 知らない合言葉は null にする。
	 * URL を手で書き換えて、知らない文字を入れても何も出ない。
	 *
	 * @param error 止まった理由の合言葉
	 * @return 画面に出す文。出すものが無ければ null
	 */
	private String messageFor(String error) {

		if (error == null) {
			return null;
		}

		return switch (error) {
			case "duePast"    -> "締切に過去の日付は指定できません。";
			case "titleLong"  -> "タスクの内容は100文字までです。";
			case "detailLong" -> "完了のヒントは300文字までです。";
			case "dueFormat"  -> "締切の形式が正しくありません。";
			default           -> null;
		};
	}

	// ===== メソッド（例外を受け止める） =====

	/**
	 * 画面から来た値を、引数の型に変換できなかったとき。
	 *
	 * 締切の欄は、最初は文字入力で、触ると日付入力に変わる作りにしてある。
	 * JavaScript が動かない状態や、開発者ツールから送ると、
	 * 日付でない文字がそのまま届く。
	 *
	 * 受け止めずにいると 500 のエラー画面になる。
	 * 壊れているのは入力であってアプリではないので、
	 * 一覧に戻して、何を直せばよいかを伝える。
	 *
	 * 難易度の欄も同じ仕組みで変換しているので、
	 * 知らない値が来たときはここに入る。どちらも入力の誤りなので、
	 * 文面は締切のものに寄せず、形式が正しくないとだけ伝える。
	 *
	 * このクラスの中で起きたものだけを受け止める。
	 * 他のコントローラの変換の失敗には関わらない。
	 *
	 * @return タスク一覧へ戻る
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public String handleTypeMismatch() {
		return "redirect:/tasks?error=dueFormat";
	}
}