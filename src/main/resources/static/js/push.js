/*
 * 通知の許可をもらい、届け先をサーバーへ送る。
 *
 * 設定画面のスイッチから呼ばれる。
 *
 * 流れは3段階。
 * 1. サービスワーカー（/sw.js）を登録する
 * 2. ブラウザに通知の許可を求める
 * 3. もらった届け先をサーバーへ送る
 *
 * 2 で断られたら、そこで終わり。あとから設定を変えるにはブラウザ側の操作が要る。
 *
 * どの段階で止まっても、スイッチは切に戻す。
 * 「入っているのに届かない」という食い違いを残さないため。
 */

// ===== 準備 =====

// CSRF の合言葉。<head> の <meta> から読む。
// fetch は何も足してくれないので、自分でヘッダーに載せる必要がある
const pushCsrfToken = document.querySelector('meta[name="_csrf"]').content;
const pushCsrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

// VAPID の公開鍵。画面の body から読む。
// サーバーが持っている秘密鍵と対になっていて、
// ブラウザは「この鍵の持ち主からの通知だけ受け取る」という約束をする
const vapidPublicKey = document.body.dataset.vapidKey;

/**
 * Base64（URL用の記号）の文字列を、バイトの並びに変換する。
 *
 * ブラウザの通知の仕組みは、鍵をバイトの並びでしか受け取らない。
 * サーバーからは文字列で渡っているので、ここで戻す。
 *
 * 末尾の = を補ってから、記号を元に戻して、1文字ずつ数値にしている。
 */
function toBytes(base64Url) {

	// 長さが4の倍数になるように = を足す
	const padding = "=".repeat((4 - (base64Url.length % 4)) % 4);

	// URL用の記号（- _）を、ふつうの Base64 の記号（+ /）に戻す
	const base64 = (base64Url + padding).replace(/-/g, "+").replace(/_/g, "/");

	// atob は Base64 を文字列に戻す関数。1文字ずつ数値にして配列に入れる
	const raw = atob(base64);
	const bytes = new Uint8Array(raw.length);

	for (let i = 0; i < raw.length; i++) {
		bytes[i] = raw.charCodeAt(i);
	}
	return bytes;
}

/**
 * サーバーへ送る。CSRF の合言葉を付けるのを毎回書かずに済ませる。
 *
 * 設定画面の効果音・紙ふぶきのスイッチも、この関数を使っている。
 * 通知とは別の用途だが、合言葉を付ける処理は同じなので、
 * ここに1つだけ置いて共有している。
 */
function postJson(url, data) {
	return fetch(url, {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			[pushCsrfHeader]: pushCsrfToken
		},
		body: JSON.stringify(data)
	});
}

// ===== 通知を入にする =====

/**
 * 通知の許可をもらい、届け先をサーバーに登録する。
 *
 * 途中で失敗したときも false を返す。
 * 例外のまま外へ出すと、呼び出し側の「切に戻す」行まで届かず、
 * スイッチが入のまま、届け先は登録されていない状態になる。
 *
 * 起きうるのは次のような場合。
 * ・VAPID の公開鍵が空、または形が正しくない
 * ・サービスワーカーの登録に失敗した
 * ・端末側の事情で subscribe が断られた
 *
 * @return 成功したら true、できなかったら false
 */
async function enablePush() {

	// この端末が通知に対応しているか。古いブラウザや一部の環境では使えない
	if (!("serviceWorker" in navigator) || !("PushManager" in window)) {
		alert("このブラウザは通知に対応していません。");
		return false;
	}

	// 鍵が無いまま進むと、後ろの subscribe が分かりにくい形で失敗する。
	// 先に見ておけば、原因が設定にあることがすぐ分かる
	if (!vapidPublicKey) {
		console.error("VAPID の公開鍵が設定されていません");
		alert("通知の設定が済んでいません。管理者に連絡してください。");
		return false;
	}

	try {
		// 1. サービスワーカーを登録する。
		//    すでに登録済みなら、そのまま同じものが返る
		const registration = await navigator.serviceWorker.register("/sw.js");

		// 登録が終わって使える状態になるまで待つ
		await navigator.serviceWorker.ready;

		// 2. 許可を求める。ブラウザが画面の上に確認を出す
		const permission = await Notification.requestPermission();

		if (permission !== "granted") {
			alert("通知が許可されませんでした。ブラウザの設定から変えられます。");
			return false;
		}

		// 3. 届け先を作る。
		//    userVisibleOnly は「必ず画面に表示する通知にだけ使う」という約束。
		//    false にはできない決まりになっている
		const subscription = await registration.pushManager.subscribe({
			userVisibleOnly: true,
			applicationServerKey: toBytes(vapidPublicKey)
		});

		// 届け先は JSON にすると { endpoint, keys: { p256dh, auth } } の形になる。
		// サーバー側の入れ物は平らな3項目なので、ここで並べ直す
		const json = subscription.toJSON();

		const response = await postJson("/push/subscribe", {
			endpoint: json.endpoint,
			p256dh: json.keys.p256dh,
			auth: json.keys.auth
		});

		// サーバーが受け取れなかったら、ブラウザ側の登録も戻す。
		// 片方だけ残すと、受け取る準備はできているのに誰も送ってこない状態になる
		if (!response.ok) {
			await subscription.unsubscribe();
			alert("通知の登録に失敗しました。もう一度お試しください。");
			return false;
		}

		return true;

	} catch (error) {
		// ここに来るのは、想定していない失敗。
		// 何が起きたかはコンソールに残し、画面には短く伝える
		console.error("通知の登録に失敗しました", error);
		alert("通知を入にできませんでした。もう一度お試しください。");
		return false;
	}
}

// ===== 通知を切にする =====

/**
 * 届け先を消して、ブラウザ側の登録も解除する。
 *
 * 両方やらないと、片方だけ残る。
 * サーバーだけ消すとブラウザは受け取る準備をしたまま、
 * ブラウザだけ解除するとサーバーは送り続けて毎回失敗する。
 *
 * 失敗しても例外を外に出さない。
 * 切ろうとしたのに途中で止まると、スイッチの見た目だけが入に戻る。
 * 利用者から見れば「切ったのに切れない」ことになる。
 */
async function disablePush() {

	if (!("serviceWorker" in navigator)) {
		return;
	}

	try {
		const registration = await navigator.serviceWorker.ready;
		const subscription = await registration.pushManager.getSubscription();

		if (subscription === null) {
			return;
		}

		await postJson("/push/unsubscribe", { endpoint: subscription.endpoint });
		await subscription.unsubscribe();

	} catch (error) {
		console.error("通知の解除に失敗しました", error);
	}
}

// ===== 画面につなぐ =====

// 要素を先に探すのではなく、画面全体で変化を見張る。
// こうしておくと、読み込む順番や id の付き方に左右されない。
//
// 前は「読み込みが終わった時点で id で探す」形にしていたが、
// 見つからないと何も起きず、エラーも出ないまま黙って効かなくなっていた。

// --- 通知のスイッチ ---
document.addEventListener("change", async function (event) {

	// 自分の担当かどうかを、id で判断する
	if (event.target.id !== "notify-enabled") {
		return;
	}

	const toggle = event.target;

	// 処理中に何度も切り替えられないようにする。
	// 許可の確認が出ているあいだも、スイッチ自体は押せてしまうため
	toggle.disabled = true;

	try {
		if (toggle.checked) {
			// できなかったら切に戻す。
			// 入っているのに届かない、という食い違いを残さない
			const ok = await enablePush();
			if (!ok) {
				toggle.checked = false;
			}
		} else {
			await disablePush();
		}
	} finally {
		// 成功しても失敗しても、必ず押せる状態に戻す
		toggle.disabled = false;
	}
});

// --- 通知を試すボタン ---
document.addEventListener("click", async function (event) {

	if (event.target.id !== "push-test") {
		return;
	}

	const response = await postJson("/push/test", {});

	if (response.ok) {
		alert("送信しました。数秒で届きます。");
	} else {
		alert("送信できませんでした。通知が入になっているか確かめてください。");
	}
});