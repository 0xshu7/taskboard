package com.example.taskboard;

// ===== インポート =====
// java.math.BigInteger … 大きな整数。鍵の中身がこの形で入っている
// java.security.*      … 鍵を作る仕組み。Java に最初から入っている
// java.security.spec.* … どの種類の鍵を作るかの指定と、座標の形
// java.util.Base64     … バイトの並びを文字列にする

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.Base64;

/**
 * VAPID の鍵を1組だけ作るための、使い捨てのクラス。
 *
 * 1回動かして、出てきた2つの値を環境変数に入れたら、このファイルは削除する。
 * アプリの一部ではないので、残しておくと「これは何だろう」と迷わせる。
 *
 * 作っているのは secp256r1（別名 P-256）という種類の鍵の組。
 * Web Push の仕様でこの種類と決まっている。
 *
 * 外部のライブラリは使わない。Java に最初から入っているもので足りる。
 */
public class VapidKeyGenerator {

	public static void main(String[] args) throws Exception {

		// P-256 の鍵の組を作る
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));

		KeyPair pair = generator.generateKeyPair();

		ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
		ECPrivateKey privateKey = (ECPrivateKey) pair.getPrivate();

		// 公開鍵は、平面上の点（X座標とY座標）で表される。
		// Web Push が求める形は「先頭に 0x04 を置き、X 32バイト、Y 32バイト」の計65バイト
		ECPoint point = publicKey.getW();

		byte[] publicBytes = new byte[65];
		publicBytes[0] = 0x04;
		System.arraycopy(toFixed32(point.getAffineX()), 0, publicBytes, 1, 32);
		System.arraycopy(toFixed32(point.getAffineY()), 0, publicBytes, 33, 32);

		// 秘密鍵はただの数値。32バイトぴったりに揃える
		byte[] privateBytes = toFixed32(privateKey.getS());

		// URLに入れられる形（記号が + / ではなく - _）で、末尾の = を付けない
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

		System.out.println();
		System.out.println("VAPID_PUBLIC_KEY=" + encoder.encodeToString(publicBytes));
		System.out.println("VAPID_PRIVATE_KEY=" + encoder.encodeToString(privateBytes));
		System.out.println();
	}

	/**
	 * 数値を32バイトの配列にする。足りなければ前をゼロで埋め、多ければ前を捨てる。
	 *
	 * BigInteger は必要な長さしか持たないので、そのまま取り出すと
	 * 31バイトになったり、符号用の 0 が付いて33バイトになったりする。
	 * 鍵は長さが決まっているので、ここで揃える。
	 *
	 * @param value 鍵の中身の数値
	 * @return 32バイトの配列
	 */
	private static byte[] toFixed32(BigInteger value) {

		byte[] source = value.toByteArray();
		byte[] result = new byte[32];

		if (source.length > 32) {
			// 先頭に符号用の 0 が付いているので、後ろ32バイトを使う
			System.arraycopy(source, source.length - 32, result, 0, 32);
		} else {
			// 短いときは後ろ詰めにして、前をゼロのままにする
			System.arraycopy(source, 0, result, 32 - source.length, source.length);
		}
		return result;
	}
}