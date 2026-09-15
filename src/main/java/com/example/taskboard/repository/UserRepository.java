package com.example.taskboard.repository;

// ===== インポート =====
// java.util.Optional … 「あるかもしれないし、無いかもしれない」入れ物
//
// 以下は Spring Data JPA の部品。
// JpaRepository … 保存・取得・削除の基本的な処理をまとめて用意してくれる
//
// 以下はこのアプリの部品。
// User … 利用者1人

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.User;

/**
 * 利用者の出し入れ。
 *
 * クラスではなくインターフェース。中身は1行も書かない。
 * 実際に動くものは Spring が起動時に自動で作る。
 *
 * JpaRepository&lt;User, Long&gt; の2つは、
 * 「扱うのは User」「主キーの型は Long」という指定。
 * これだけで次のようなものが使えるようになる。
 *
 * save(user)     … 保存する。IDが無ければ追加、あれば更新
 * findById(id)   … IDで1件取り出す。無いこともあるので Optional で返る
 * findAll()      … 全件取り出す
 * delete(user)   … 削除する
 * count()        … 件数を数える
 *
 * 独自の検索は、メソッド名を決まった形で書くだけでよい。
 * SQL は書かない。
 */
public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * メールアドレスで利用者を探す。
	 *
	 * メソッド名がそのまま検索条件になる。
	 * findBy のあとに項目名を書くと、その列で探す SQL が組み立てられる。
	 *
	 *   findByEmail(String email)
	 *   → SELECT * FROM users WHERE email = ?
	 *
	 * 項目名は User のフィールド名と一致させる。
	 * 列名（email）ではなくフィールド名で書く点に注意する。
	 * 綴りを間違えるとコンパイルは通り、起動したときに失敗する。
	 *
	 * 戻り値が Optional なのは、見つからないことがあるため。
	 * null を返す代わりに「空の入れ物」を返すことで、
	 * 受け取る側が確認を忘れにくくなる。
	 *
	 * ログイン機能で使う想定。現在は DevDataLoader が起動時に使っている。
	 *
	 * @param email 探すメールアドレス
	 * @return 見つかった利用者。無ければ空
	 */
	Optional<User> findByEmail(String email);
	
	/**
	 * Google のアカウント識別子で利用者を探す。
	 *
	 * sub は Google が1アカウントに1つ割り当てる文字列で、変わらない。
	 * メールアドレスは本人が変更できるので、そちらを鍵にすると
	 * 変更したときに別人として扱われてしまう。
	 *
	 * @param googleSub Google のアカウント識別子
	 * @return 見つかった利用者。無ければ空
	 */
	Optional<User> findByGoogleSub(String googleSub);
}