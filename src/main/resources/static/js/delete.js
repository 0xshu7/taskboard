// 削除をページの再読み込みなしで行う
// 押した瞬間に画面から消し、サーバーへの連絡は裏で送る

// ページ全体で「送信」を見張る。あとから増えた行にも効く書き方
document.addEventListener('submit', function (event) {

  const form = event.target;

  // 削除フォーム以外は普通に送信させる
  if (!form.classList.contains('delete-form')) {
    return;
  }

  // ページの再読み込みを止める
  event.preventDefault();

  const row = form.closest('.task');

  // 完了済みだったかを、消す前に覚えておく
  const wasDone = row.classList.contains('task-DONE');

  // すぐに消す
  row.remove();

  // 未完了のタスクを消したときだけ、残り件数を1減らす
  if (!wasDone) {
    const span = document.getElementById('remaining-num');
    span.textContent = Number(span.textContent) - 1;
  }

  // サーバーへ削除を伝える。結果は待たない
  fetch(form.action, { method: 'POST' });
});