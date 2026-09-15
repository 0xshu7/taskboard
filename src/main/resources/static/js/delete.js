// 削除をページの再読み込みなしで行う
// 押した瞬間に画面から消し、サーバーへの連絡は裏で送る

// CSRF の合言葉。<head> の <meta> から読む。
// フォームで送るときは Thymeleaf が隠し項目として自動で入れてくれるが、
// fetch は何も足してくれないので、自分でヘッダーに載せる必要がある。
// これが無いと、サーバーは 403 で弾いて何も起きない
const csrfToken = document.querySelector('meta[name="_csrf"]').content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

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

  // サーバーへ削除を伝える。結果は待たない。
  // ヘッダー名は決め打ちにせず、meta から読んだものを使う。
  // [csrfHeader] と角括弧で囲むと、変数の中身を項目名として使える
  fetch(form.action, {
    method: 'POST',
    headers: { [csrfHeader]: csrfToken }
  });
});