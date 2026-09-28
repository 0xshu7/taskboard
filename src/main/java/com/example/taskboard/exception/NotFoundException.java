package com.example.taskboard.exception;

// ===== インポート =====
// 以下は Spring の部品。
// HttpStatus     … HTTPの状態コード。404 や 500 を表す
// ResponseStatus … この例外が投げられたとき、どの状態コードで返すかを決める印

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 「そんなものは無い」を表す例外。
 *
 * 404（見つかりません）で返る。
 *
 * 【なぜ専用の例外を作るか】
 *
 * これまでは IllegalArgumentException を投げていたが、
 * Spring はそれを 500（サーバーの不具合）として扱う。
 *
 * 500 は「アプリが壊れた」という意味で、404 は「そんなものは無い」という意味。
 * 他人のタスクを指されたときに返すべきなのは後者になる。
 * 壊れていないのに壊れたと言うと、本当に壊れたときに見分けがつかない。
 *
 * 【他人のものと、存在しないものを同じ扱いにする】
 *
 * このアプリでは、次の2つで同じ例外を投げている。
 *
 * 　1. そのIDのものが存在しない
 * 　2. 存在するが、他人のもの
 *
 * 分けて「あなたのものではありません」と返すと、
 * そのIDのデータが存在することを相手に教えることになる。
 * 番号を順に試せば、誰かが何件持っているかが分かってしまう。
 *
 * @ResponseStatus を付けておくと、
 * この例外が処理されずに外まで出たとき、Spring が自動で 404 にする。
 * コントローラごとに try-catch を書く必要がない。
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

	/**
	 * 直列化の版番号。
	 *
	 * 例外は Serializable なので、付けないと警告が出る。
	 * 値そのものに意味はなく、1 のままでよい。
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * メッセージを持たせて作る。
	 *
	 * @param message 何が見つからなかったか
	 */
	public NotFoundException(String message) {
		super(message);
	}
}