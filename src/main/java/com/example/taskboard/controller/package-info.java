/**
 * 画面とのやりとりを受け持つ場所。
 *
 * ブラウザからの要求を受け取り、service に処理を任せ、
 * 結果を画面へ渡すところまでを行う。
 * 判断や計算はここには書かず、service に置く。
 *
 * ここに書くもの：
 * ・どのURLで何の画面を出すか（@GetMapping）
 * ・送られてきた入力をどう受け取るか（@RequestParam / @ModelAttribute）
 * ・画面へ何を渡すか（Model）
 * ・処理のあとどこへ移るか（redirect:）
 *
 * @Controller は HTML を返す。文字や JSON をそのまま返す @RestController とは別物。
 */
package com.example.taskboard.controller;