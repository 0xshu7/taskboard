/**
 * AI とのやりとりを受け持つ場所。
 *
 * 外のサービスを呼ぶところなので、このアプリで一番うまくいかないことが多い。
 * 応答が遅い、形式が崩れる、混み合って断られる、1日の上限に達する。
 * そのため、失敗を前提にした作りにしてある。
 *
 * 入口はインターフェース1つ（ProposalGenerator）にまとめてある。
 * 設定によって、固定の3案を返すものと、実際に AI を呼ぶものが入れ替わる。
 * 呼び出す側は、どちらが入っているかを知らないまま使える。
 *
 * それでも失敗したときは FallbackCards の固定3案に切り替える。
 * どこから来た3案かは ProposalSet.source に残すので、
 * あとから失敗の割合を数えられる。
 *
 * 入っているもの：
 * ProposalGenerator     … 窓口となるインターフェース
 * MockProposalGenerator … 固定3案を返す（ai.mock=true）
 * RealProposalGenerator … Gemini を呼ぶ（ai.mock=false）
 * FallbackCards         … 失敗したときの固定3案
 * PromptBuilder         … AI に送る文章を組み立てる
 */
package com.example.taskboard.ai;