/*
 * 音と花吹雪。
 *
 * 音量は <body data-volume="50"> から読む。設定画面で保存した値が入っている。
 * 0 のときは鳴らさない。
 */

// ===== 音量 =====

// body の data-volume を 0〜1 の値にする。無ければ 0.5（半分）
const VOLUME = (function () {
	const raw = document.body.dataset.volume;
	if (raw === undefined) {
		return 0.5;
	}
	return Number(raw) / 100;
})();

// 同じ音を何度も読み込まないように、一度作ったものを取っておく
const soundCache = {};

/**
 * 音を鳴らす。name は "start" / "select" / "retopic" / "complete"
 */
function playSound(name) {
	if (VOLUME <= 0) {
		return;                       // 音量ゼロなら何もしない
	}

	if (!soundCache[name]) {
		soundCache[name] = new Audio("/sounds/" + name + ".wav");
	}

	const sound = soundCache[name];
	sound.volume = VOLUME;
	sound.currentTime = 0;            // 連打しても頭から鳴るように戻す
	sound.play().catch(function () {
		// ブラウザが音を止めることがある。止まっても画面は動かしたいので、ここでは何もしない
	});
}

// ===== 花吹雪 =====

// ブランドの3色に白を足したもの
const CONFETTI_COLORS = ["#FF2D8A", "#7ED321", "#00C2D1", "#FFFFFF"];

// 花吹雪を出しておく長さ（ミリ秒）と、薄くし始める時刻
const CONFETTI_LIFE = 3800;
const CONFETTI_FADE_START = 3200;

/**
 * 画面いっぱいに紙吹雪を降らせる
 */
function showConfetti() {
	// 画面の一番上に、透明な板を1枚置く
	const canvas = document.createElement("canvas");
	canvas.className = "confetti-canvas";
	canvas.width = window.innerWidth;
	canvas.height = window.innerHeight;
	document.body.appendChild(canvas);

	const ctx = canvas.getContext("2d");

	// 紙を140枚つくる。1枚ずつ、位置・速さ・回り方を変える
	const pieces = [];
	for (let i = 0; i < 140; i++) {
		pieces.push({
			x: Math.random() * canvas.width,
			// 画面の上の外から降らせる。散らす幅を広くすると、降り続く時間が長くなる
			y: -20 - Math.random() * canvas.height * 1.2,
			w: 6 + Math.random() * 6,
			h: 10 + Math.random() * 8,
			color: CONFETTI_COLORS[Math.floor(Math.random() * CONFETTI_COLORS.length)],
			speedY: 1.8 + Math.random() * 2.6,
			speedX: -1.2 + Math.random() * 2.4,
			angle: Math.random() * Math.PI * 2,
			spin: -0.15 + Math.random() * 0.3,
			// 横揺れの速さ。ひらひら感を出すため
			swayStep: 0.02 + Math.random() * 0.04,
			sway: Math.random() * Math.PI * 2
		});
	}

	const startTime = Date.now();

	function draw() {
		const elapsed = Date.now() - startTime;

		// 時間がきたら板ごと片づける
		if (elapsed > CONFETTI_LIFE) {
			canvas.remove();
			return;
		}

		// 最後だけだんだん薄くする
		if (elapsed > CONFETTI_FADE_START) {
			canvas.style.opacity = (CONFETTI_LIFE - elapsed) / (CONFETTI_LIFE - CONFETTI_FADE_START);
		}

		ctx.clearRect(0, 0, canvas.width, canvas.height);

		for (const p of pieces) {
			p.y += p.speedY;
			p.sway += p.swayStep;
			p.x += p.speedX + Math.sin(p.sway) * 1.2;   // 左右にひらひら揺らす
			p.angle += p.spin;

			// 紙を回転させて描く
			ctx.save();
			ctx.translate(p.x, p.y);
			ctx.rotate(p.angle);
			ctx.fillStyle = p.color;
			ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
			ctx.restore();
		}

		requestAnimationFrame(draw);   // 次の1コマを描く
	}

	draw();
}

/**
 * 押した行を、その場で「完了」の見た目に変える。
 * データベースはまだ変わっていないが、先に見せることで待ち時間を感じさせない。
 */
function showAsDone(form, button) {
	button.disabled = true;              // 二重に押されないように
	button.textContent = "完了";
	button.classList.remove("tag-DOING");
	button.classList.add("tag-DONE");

	// 行そのものも完了の見た目にする（取り消し線などが付く）
	const row = form.closest(".task");
	if (row) {
		row.classList.remove("task-DOING");
		row.classList.add("task-DONE");
	}
}

// ===== ボタンに結びつける =====

/**
 * 送信をいったん止めて、音を鳴らしてから送る。
 *
 * そのまま送ると、音が鳴り始める前に画面が切り替わって聞こえない。
 * waitMs だけ待ってから送ることで、音が聞こえるようにする。
 *
 * waitingLabel を渡すと、送ったあとボタンの文字をそれに変えて押せなくする。
 * AIの応答に3〜5秒かかるものだけに付ける。
 * 何も起きないように見える時間があると、人はもう一度押す。
 */
function playThenSubmit(form, soundName, waitMs, waitingLabel) {
	form.addEventListener("submit", function (event) {
		// すでに一度処理したものは、そのまま通す（無限に止まらないように）
		if (form.dataset.submitting === "true") {
			return;
		}

		event.preventDefault();
		form.dataset.submitting = "true";

		playSound(soundName);

		// 待たせるものだけ、ボタンの見た目を変える
		if (waitingLabel) {
			const button = form.querySelector("button");
			if (button) {
				button.disabled = true;          // 二重に押されないように
				button.textContent = waitingLabel;
			}
			showWaiting(waitingLabel); 	
		}

		setTimeout(function () {
			form.submit();
		}, waitMs);
	});
}

document.addEventListener("DOMContentLoaded", function () {

	// --- スタートボタン ---
	// 押してからAIが3案を返すまで3〜5秒かかる。その間ボタンで知らせる
	const startForm = document.querySelector(".start-form");
	if (startForm) {
		playThenSubmit(startForm, "start", 250, "AIが考えています…");
	}

	// --- 3択の「これにする」 ---
	// スタートと同じ音にする。「決めて動き出す」ときの音を揃えたいので。
	// こちらはAIを呼ばないのですぐ切り替わる。文字は変えない
	document.querySelectorAll(".choose-form").forEach(function (form) {
		playThenSubmit(form, "start", 250);
	});

	// --- 「別の話題にする」 ---
	// ここも作り直すので待つ
	const retopicForm = document.querySelector(".retopic-form");
	if (retopicForm) {
		playThenSubmit(startForm, "start", 250, "AIが考えています…");
	}

	// --- 状態のボタン ---
	// 未実施 → 進行中 のときは、スタートと同じ音。
	// 進行中 → 完了   のときは、完了音と花吹雪。
	// 完了   → 未実施 のときは、何もしない。
	document.querySelectorAll(".status-form").forEach(function (form) {
		const button = form.querySelector("button");

		// これから動き出すとき
		if (button.dataset.begin === "true") {
			playThenSubmit(form, "start", 250);
			return;
		}

		// やり切ったとき。花吹雪を見せたいので長めに待つ
		if (button.dataset.celebrate === "true") {
			form.addEventListener("submit", function (event) {
				event.preventDefault();

				// サーバの返事を待たずに、画面のほうを先に「完了」にする。
				// 花吹雪が降っている間、完了になった姿を見てもらうため
				showAsDone(form, button);

				playSound("complete");
				showConfetti();

				setTimeout(function () {
					form.submit();
				}, 2600);
			});
		}
	});
});

// ===== 待っているあいだの表示 =====

/**
 * 画面全体を覆って「考えています」を出す。
 *
 * AIの応答に4〜5秒かかる。その間なにも変わらないと、
 * 押せていないと思ってもう一度押される。
 *
 * ボタンの文字を変えるだけでは、画面の他の部分が止まって見える。
 * 画面全体を覆うことで「いま待つ時間です」と分かるようにしている。
 */
function showWaiting(text) {
	const overlay = document.createElement("div");
	overlay.className = "waiting-overlay";

	overlay.innerHTML =
		'<div class="waiting-box">' +
			'<div class="waiting-face">🤖</div>' +
			'<p class="waiting-text">' + text + '</p>' +
			'<div class="waiting-dots"><span></span><span></span><span></span></div>' +
		'</div>';

	document.body.appendChild(overlay);
}