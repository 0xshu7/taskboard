/*
 * サービスワーカー。
 *
 * ふつうの JavaScript と違い、画面とは別のところで動く。
 * 画面を閉じていてもブラウザが動いていれば生きているので、
 * 通知を受け取って表示できるのはこれだけ。
 *
 * このファイルは必ずアプリの一番上（/sw.js）に置く。
 * /js/sw.js のように下に置くと、そこから下しか担当できなくなる。
 */

// アプリの名前。中身が読めなかったときの見出しに使う。
// 画面に出る文字なので、名前を変えたらここも変える
const APP_NAME = "さんたく";

// ===== 通知を受け取る =====

/**
 * サーバーから通知が届いたとき。
 *
 * event.data には、サーバーが送った JSON が入っている。
 * NotificationService が {"title":"...","body":"..."} の形で作っている。
 */
self.addEventListener("push", function (event) {

	// 中身が無いこともある。そのときは既定の文言にする
	let payload = { title: APP_NAME, body: "" };

	if (event.data) {
		try {
			payload = event.data.json();
		} catch (e) {
			// JSON として読めない場合は、そのまま文字として扱う。
			// 見出しは既定のままにして、本文に中身を入れる
			payload.body = event.data.text();
		}
	}

	// waitUntil は「この処理が終わるまで、サービスワーカーを止めないで」という指示。
	// これが無いと、表示し終える前に止められることがある
	event.waitUntil(
		self.registration.showNotification(payload.title, {
			body: payload.body,
			icon: "/icon-192.png",
			badge: "/icon-192.png",
			// 同じタグの通知は上書きされる。何通も積み上がらないようにするため
			tag: "taskboard-due",
			// 上書きのときも、あらためて知らせる。
			// これが無いと、2回目以降は静かに差し替わるだけで画面に出ない
			renotify: true
		})
	);
});

// ===== 通知を押したとき =====

/**
 * 通知をクリックしたら、タスク一覧を開く。
 *
 * すでにこのアプリのタブが開いていれば、そちらを前に出す。
 * 開いていなければ新しく開く。
 * 押すたびにタブが増えると、それ自体が面倒になる。
 */
self.addEventListener("notificationclick", function (event) {

	event.notification.close();

	event.waitUntil(
		clients.matchAll({ type: "window", includeUncontrolled: true }).then(function (windows) {

			for (const window of windows) {
				if (window.url.includes("/tasks")) {
					return window.focus();
				}
			}
			return clients.openWindow("/tasks");
		})
	);
});