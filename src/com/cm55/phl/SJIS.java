// Created by Cryptomedia Co., Ltd. 2006/06/06
package com.cm55.phl;

import java.util.*;

/**
 * Shift-JISバッファ
 * <p>
 * JavaのStringの代わりに多くの場所でこれを用いる。PHLでは内部でShift-JISが使用
 * されているため、Stringのままでは文字列処理がしにくい。もしStringのままで処理
 * してしまうと、
 * </p>
 * <ul>
 * <li>文字列の「長さ」を知るためには結局Shift-JISに一度変換しなければならない。
 * <li>部分文字列の取り出し方法がShift-JISとは異なるので、やはりこれもその都度
 * Shift-JISに変換しなければならない。
 * </ul>
 * <p>
 * なお、SJISクラスはStringと同じでImmutableである。一度作成されると、その中身が
 * 変更されることはない。
 * </p>
 */
public class SJIS  implements Comparable<SJIS> {

  public static final String ENCODING = "Windows-31J";

  public static final byte SPACE = (byte)0x20;

  /** バイトバッファ */
  protected byte[]bytes;

  /** 種類バッファ */
  protected byte[]kinds;

  /** 内部使用 */
  private SJIS() {
  }

  /** size分の空白を作成 */
  public SJIS(int size) {
    this(size, SPACE);
  }

  /** size分の指定半角文字を作成 */
  public SJIS(int size, byte b) {
    bytes = new byte[size];
    Arrays.fill(bytes, b);
  }

  /** 文字列をバイト配列化して作成 */
  public SJIS(String string) {
    try {
      bytes = string.getBytes(ENCODING);
    } catch (Exception ex) {
      throw new InternalError();
    }
  }

  /** バイト配列から作成 */
  public SJIS(byte[]input) {
    this(input, 0, input.length);
  }

  /** バイト配列の位置とサイズから作成 */
  public SJIS(byte[]input, int pos, int size) {

    if (size == 0) {
      bytes = new byte[0];
      return;
    }

    // まずは素直にコピーする。
    bytes = new byte[size];
    System.arraycopy(input, pos, bytes, 0, size);

    // トップ位置が漢字後半なら補正
    if (sjisKind(input, pos) == 2) bytes[0] = SPACE;

    // kinds配列を作成
    getKinds();

    // 最後の文字が漢字の前半なら補正
    if (kinds[size - 1] == 1) {
      bytes[size - 1] = SPACE;
      kinds[size - 1] = 0;
    }
  }
  
  /** 指定バイト列のidx番目のバイトの種類を得る
   * 0:ANK、 1:漢字前半、2:漢字後半
   */
  public static int sjisKind(byte[]sjis, int idx) {
    if (idx >= sjis.length)
      throw new InternalError();
    int i = 0;
    while (true) {
      if (isKanji(sjis[i])) {
        if (i == idx) return 1;
        if (i == idx - 1) return 2;
        i += 2;
      } else {
        if (i == idx) return 0;
        i++;
      }
    }
  }

  /** 複数のSJISを連結して作成 */
  public SJIS(SJIS...input) {
    int total = 0;
    for (SJIS in: input)
      total += in.length();
    bytes = new byte[total];
    int index = 0;
    for (SJIS in: input) {
      System.arraycopy(in.bytes, 0, bytes, index, in.bytes.length);
      index += in.bytes.length;
    }
  }

  /** 長さを取得 */
  public int length() {
    return bytes.length;
  }

  /** 接続 */
  public SJIS append(SJIS sjis) {
    byte[]newBytes = new byte[bytes.length + sjis.length()];
    System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
    System.arraycopy(sjis.bytes, 0, newBytes, bytes.length, sjis.bytes.length);
    return new SJIS(newBytes);
  }

  /** 置換 */
  public SJIS replace(int index, SJIS sjis) {
    SJIS left = extract(0, index);
    SJIS right = extract(index + sjis.length());
    return left.append(sjis).append(right);
  }

  /** 一部を取り出す */
  public SJIS extract(int start) {
    return new SJIS(bytes, start, bytes.length - start);
  }

  /** 一部を取り出す */
  public SJIS extract(int start, int size) {
    return new SJIS(bytes, start, size);
  }

  /** 指定位置のバイトを取り出す */
  public byte byteAt(int index) {
    return bytes[index];
  }

  /** 指定位置の種類を取り出す */
  public byte kindAt(int index) {
    return getKinds()[index];
  }

  /** 指定位置のUNICODE文字を取得する。
   * ただし漢字の後半はだめ */
  public char charAt(int index) {
    int kind = getKinds()[index];
    assert(kind != 2);
    try {
      if (kind == 0)
        return new String(bytes, index, 1, ENCODING).charAt(0);
      else
        return new String(bytes, index, 2, ENCODING).charAt(0);
    } catch (Exception ex) {
      throw new InternalError();
    }
  }

  /** 最後のバイトを削除する */
  public SJIS removeLast() {
    return new SJIS(bytes, 0, bytes.length - 1);
  }

  /** 最大limitサイズにする */
  public SJIS limit(int size) {
    if (bytes.length <= size) return this;
    return new SJIS(bytes, 0, size);
  }

  /** 指定サイズにする。大きい場合は小さくする。足りない場合は右側を空白で埋める */
  public SJIS forceSize(int size) {
    return forceSize(size, SPACE);
  }

  /** 指定サイズにする。大きい場合は小さくする。足りない場合は右側をpaddingで埋める */
  public SJIS forceSize(int size, byte padding) {
    if (bytes.length == size) return this;
    if (bytes.length > size) return limit(size);
    return append(new SJIS(size - bytes.length, padding));
  }

  /** 文字列化 */
  public String toString() {
    try {
      return new String(bytes, ENCODING);
    } catch (Exception ex) {
      throw new InternalError();
    }
  }

  /** 等価性 */
  public boolean equals(Object o) {
    if (!(o instanceof SJIS)) return false;
    byte[]that = ((SJIS)o).bytes;
    if (bytes.length != that.length) return false;
    for (int i = 0; i < bytes.length; i++)
      if (bytes[i] != that[i]) return false;
    return true;
  }

  /** ハッシュコード */
  public int hashCode() {
    int code = 0;
    for (int i = 0; i < bytes.length; i++)
      code += bytes[i];
    return code;
  }

  /** 前後の空白を取り除く。たしか漢字の後半として0x20は使われてないはず */
  public SJIS trim() {
    int start = 0;
    for (; start < bytes.length; start++) {
      if (bytes[start] != 0x20) break;
    }

    int end = bytes.length - 1;
    for (; end >= 0; end--) {
      if (bytes[end] != 0x20) break;
    }

    if (start == 0 && end == bytes.length - 1)
      return this;

    if (end < start)
      return new SJIS(0);

    SJIS result = extract(start, end - start + 1);
    return result;
  }

  /** 比較する */
  public int compareTo(SJIS sjis) {
    byte[]that = sjis.bytes;
    for (int i = 0; i < bytes.length && i < that.length; i++) {
      int r = bytes[i] - that[i];
      if (r != 0) return r;
    }
    if (bytes.length == that.length) return 0;
    if (bytes.length < that.length) return -1;
    return 1;
  }

  /** 文字列データを取得する */
  public byte[]getBytes() {
    byte[]newBytes = new byte[bytes.length];
    System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
    return newBytes;
  }


  private byte[]getKinds() {
    if (kinds != null) return kinds;
    kinds = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      if (isKanji(bytes[i])) {
        kinds[i] = 1;
        if (i + 1 < bytes.length) kinds[i + 1] = 2;
        i++;
      } else {
        kinds[i] = 0;
      }
    }
    return kinds;
  }

  /**
   * 漢字の前半であるか。
   * <p>
   * ちなみにShift-JISでは漢字の後半であることを判断する方法はない。
   * 通常は、あるバイトが「ANK(半角カナ含む）」か「漢字前半」かを判断し、
   * もし「漢字前半」であれば、その次の文字は「漢字後半」であるとしなければならない。
   * </p>
   */
  public static boolean isKanji(byte b) {
    int c = ((int)b) & 0xff;
    return 0x81 <= c && c <= 0x9f || 0xe0 <= c && c <= 0xef;
  }
  
//  /////////////////////////////////////////////////////////////////////////////
//  // Shift-JISユーティリティ
//  /////////////////////////////////////////////////////////////////////////////
//
//  /**
//   * 漢字の前半であるか。
//   * <p>
//   * ちなみにShift-JISでは漢字の後半であることを判断する方法はない。
//   * 通常は、あるバイトが「ANK(半角カナ含む）」か「漢字前半」かを判断し、
//   * もし「漢字前半」であれば、その次の文字は「漢字後半」であるとしなければならない。
//   * </p>
//   */
//  private static boolean isKanji(byte b) {
//    int c = ((int)b) & 0xff;
//    return 0x81 <= c && c <= 0x9f || 0xe0 <= c && c <= 0xef;
//  }
//
//  /** 指定バイト列のidx番目のバイトの種類を得る
//   * 0:ANK、 1:漢字前半、2:漢字後半
//   */
//  private static int sjisKind(byte[]sjis, int idx) {
//    if (idx >= sjis.length)
//      throw new InternalError();
//    int i = 0;
//    while (true) {
//      if (isKanji(sjis[i])) {
//        if (i == idx) return 1;
//        if (i == idx - 1) return 2;
//        i += 2;
//      } else {
//        if (i == idx) return 0;
//        i++;
//      }
//    }
//  }
}
