// Created by Cryptomedia Co., Ltd. 2006/06/07
package com.cm55.phl;

import java.io.*;
import java.util.*;

import com.cm55.phl.PHL.*;

/**
 * htcファイルの読込ユーティリティ
 *
 */
public class HTCReader {

  //private static final CLog log = CLogFactory.get(HTCReader.class);

  /** 入力ストリーム */
  protected InputStream in;

  /** 入力ストリーム中の読み込んだサイズ */
  protected int readPosition;

  /** 解析中の行の番号 */
  protected int lineNumber;

  /** 解析中の行の入力ストリーム中での位置 */
  protected int linePosition;

  /** 解析中の行 */
  protected SJIS targetLine;

  /** 行の解析インデックス */
  protected int getIndex;

  public HTCReader(InputStream in) {
    this.in = in;
    targetLine = getLine();
    getIndex = 0;
  }

  /** 現在の行の先頭位置を取得 */
  public int getPosition() {
    return linePosition;
  }

  /** パラメータをすべて使い果たしたことの確認 */
  public void endOfLine() {
    if (targetLine.length() != getIndex)
      readException("パラメータが多すぎます");
    targetLine = getLine();
    getIndex = 0;
  }

  public void unget(int unget) {
    getIndex -= unget;
    assert(getIndex >= 0);
  }

  public boolean eof() {
    return targetLine == null;
  }

  /** 残りのパラメータの頭から指定バイト分を取得 */
  public SJIS getBytes(int size) {
    //if (log.ist())      log.trace("getBytes " + targetLine + "," + getIndex + "," + size);

    SJIS result = targetLine.extract(getIndex, size);
    getIndex += size;
    return result;
  }

  /** 残りすべてのバイトを取得 */
  public SJIS getAllBytes() {
    return getBytes(targetLine.length() - getIndex);
  }

  /** 残りのパラメータの頭から指定バイト数分を数字として、整数を取得 */
  public int getInt(int size) {
    return Integer.parseInt(getBytes(size).toString());
  }

  public Register getRegister() {
    return getRegister(true);
  }

  /** 残りのパラメータの頭から５バイトをレジスター名として、そのレジスターを取得。
   * 取得できない場合、force=trueのときは例外、falseのときはnullを返す
   */
  public Register getRegister(boolean force) {
    SJIS bytes = getBytes(5);
    Register reg = Register.findRegister(bytes.trim());
    if (!force) return reg;
    if (reg == null) {
      readException("変数名ではありません:" + bytes);
    }
    return reg;
  }

  /** 残りのパラメータの頭から３バイトをキー値として取得 */
  public Key getKey() {
    String str = getBytes(3).toString();
    if (str.equals("   ")) return null;
    return Key.findKey(Integer.parseInt(str));
  }

  /** KeySetを取得 */
  public EnumSet<Key>getKeySet() {
    EnumSet<Key>keySet = EnumSet.noneOf(Key.class);
    for (int i = 0; i < Key.SPECIAL_KEYS_IN_COMMAND; i++) {
      Key key = getKey();
      if (key != null) keySet.add(key);
    }
    return keySet;
  }

  /** 残りのパラメータの頭から２バイトを比較オペレータとして取得 */
  public Comp getComp() {
    return Comp.findComp(getBytes(2).trim());
  }

  /** 残りのパラメータの頭から１２バイトをファイル名として取得 */
  public Filename getFilename() {
    SJIS name = getBytes(12).trim();
    Register register = Register.findRegister(name);
    if (Register.datSet.contains(register))
      return new Filename(register);
    return new Filename(name);
  }


  /** 残りのパラメータの頭から４バイトをファイル位置種類として取得 */
  public FilePos getFilePos() {
    SJIS name = getBytes(4).trim();
    FilePos pos = FilePos.findPos(name);
    if (pos == null) {
      readException("ファイル位置指定ではありません：" + name);
    }
    return pos;
  }


  /** 指定バイト数スキップ */
  public void skip(int size) {
    getBytes(size);
  }

  /** フル桁入力時アクションを取得 */
  public FullAction getFullAction() {
    FullAction act = FullAction.find(getInt(1));
    if (act == null) readException("フル桁入力時アクションが異常です");
    return act;
  }

  /** 文字列化 */
  public String toString() {
    return targetLine.extract(getIndex).toString();
  }

  protected SJIS getLine() {
    try {
      return doGetLine();
    } catch (IOException ex) {
      readException("読み込めません：" + lineNumber);
      return null;
    }
  }

  /** 行を取得する */
  protected SJIS doGetLine() throws IOException {
    ByteArrayOutputStream pending = new ByteArrayOutputStream();

    linePosition = readPosition;
    lineNumber++;

    while (true) {
      int c = in.read();
      if (c < 0) return null;
      if (c == '\r') {
        if (in.read() != '\n') readException("行の形式が異常です");
        break;
      }
      pending.write(c);
    }

    pending.close();
    byte[]bytes = pending.toByteArray();
    SJIS result = new SJIS(bytes, 0, bytes.length);
    pending = new ByteArrayOutputStream();

    readPosition += result.length() + 2;

    try {
      int size = Integer.parseInt(result.extract(0, 3).toString());
      if (size != result.length() + 2)
        readException("行の形式が異常です");
    } catch (Exception ex) {
      readException("行の形式が異常です");
    }


    SJIS sjis = result.extract(3);
    //if (log.ist())      log.trace("getLine[" + sjis + "]");

    return sjis;
  }

  public void parseException() {
    throw new ReadException(lineNumber);
  }
  public void readException(String s) {
    throw new ReadException(lineNumber, s);
  }
  public void parseException(String s, Exception ex) {
    throw new ReadException(lineNumber, s, ex);
  }

}
