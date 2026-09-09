// タスクの行をドラッグして並べ替える処理
// マウスでも指でも同じように動くように、pointer イベントを使う

// 対象の要素をまとめて取っておく
const list = document.getElementById('task-list');
const form = document.getElementById('reorder-form');
const idsInput = document.getElementById('reorder-ids');

// 今つかんでいる行。つかんでいないときは null
let dragging = null;

// --- つかむ ---
// ⠿ の部分を押したときだけ始める（ボタンや入力欄では始めない）
list.addEventListener('pointerdown', function (event) {

  // 押された場所が ⠿ でなければ何もしない
  if (!event.target.classList.contains('drag-handle')) {
    return;
  }

  // 押された場所から一番近い <li> を探す
  dragging = event.target.closest('.task');

  // 見た目を変えて「つかんでいる」と分かるようにする
  dragging.classList.add('dragging');

  // 指やマウスがどこへ動いても、イベントをこの要素で受け取り続ける
  event.target.setPointerCapture(event.pointerId);
});

// --- 動かす ---
list.addEventListener('pointermove', function (event) {

  // つかんでいないときは何もしない
  if (dragging === null) {
    return;
  }

  // 指で動かしたときに画面がスクロールしないようにする
  event.preventDefault();

  // 今の指の位置より下にある行を探す
  const target = findRowBelow(event.clientY);

  if (target === null) {
    // 下に行が無い＝一番下なので、末尾へ移す
    list.appendChild(dragging);
  } else {
    // 見つかった行の直前へ差し込む
    list.insertBefore(dragging, target);
  }
});

// --- 離す ---
list.addEventListener('pointerup', function () {

  if (dragging === null) {
    return;
  }

  dragging.classList.remove('dragging');
  dragging = null;

  // 並び替えた結果をサーバーへ送る
  save();
});

// 指定した高さより下にある最初の行を返す。無ければ null
function findRowBelow(y) {

  // つかんでいる行は判定から外す
  const rows = list.querySelectorAll('.task:not(.dragging)');

  for (const row of rows) {
    // その行の画面上の位置と大きさ
    const box = row.getBoundingClientRect();

    // 行の中心より上に指があるなら、この行の前に入れる
    if (y < box.top + box.height / 2) {
      return row;
    }
  }
  return null;
}

// 今の並び順をフォームに入れて送信する
function save() {

  // 上から順に data-id を集める
  const ids = [];
  for (const row of list.querySelectorAll('.task')) {
    ids.push(row.dataset.id);
  }

  // "3,1,2" のようなカンマ区切りの文字列にする
  idsInput.value = ids.join(',');

  form.submit();
}