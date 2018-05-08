// Created by Cryptomedia Co., Ltd. 2006/06/07
package com.cm55.phl.gen;

import java.io.*;
import java.util.*;

import com.cm55.phl.*;
import com.cm55.phl.PHL.*;

/**
 * HTCファイルの書き込み
 */
public class HTCWriter {

  private static final SJIS CRLF = new SJIS("\r\n");

  /** 出力先ストリーム */
  protected OutputStream out;

  /** ストリームへの現在の出力位置 */
  protected int position;

  /** 行データの保留バッファ
   * <p>
   * 一つの行にはその先頭に行全体のサイズを書き込む必要があるため、上位には
   * サイズ以外を出力させてそれを保留しておき、最後に行の先頭にサイズとコマンドヘッダ
   * を書き出して出力ストリームに書き込む
   * </p>
   */
  protected ByteArrayOutputStream pending;

  /** 出力先を指定して作成する */
  public HTCWriter(OutputStream out) {
    this.out = out;
  }

  /** 現在の位置を取得 */
  public int getPosition() {
    return position;
  }

  /** 書き込む */
  protected void write(String string, int size) {
    try {
      SJIS sjis = new SJIS(string);
      if (sjis.length() != size) throw new WriteException("書き込めません");
      write(sjis);
    } catch (Exception ex) {
      throw new InternalError();
    }
  }

  /** 書き込む */
  protected void write(SJIS sjis) {
    if (pending == null) {
      pending = new ByteArrayOutputStream();
    }
    try {
      pending.write(sjis.getBytes());
    } catch (Exception ex) {
      throw new InternalError();
    }
  }

  /** 文字列を書き込む */
  public void putString(String s) {
    write(s, s.length());
  }

  /** 整数値を指定幅で書き込む */
  public void putInt(int value, int size) {
    try (Formatter f = new Formatter()) {
      f.format("%0" + size + "d", value);
      write(f.toString(), size);
    }
  }

  /** レジスターを５バイトで書き込む。nullの場合は空白を書き込む */
  public void putRegister(Register register) {
    if (register == null) {
      write("     ", 5);
      return;
    }
    try (Formatter f = new Formatter()) {
      f.format("%-5s", register.toString());
      write(f.toString(), 5);
    }
  }

  /** キー値を３バイトで書き込む。nullの場合は空白 */
  public void putKey(Key key) {
    if (key == null) {
      write("   ", 3);
      return;
    }
    putInt(key.code(), 3);
  }

  public void putKeySet(EnumSet<Key>keySet) {
    assert(keySet.size() <= Key.SPECIAL_KEYS_IN_COMMAND);
    int count = 10;
    for (Key key: keySet) {
      putKey(key);
      count--;
    }
    while (count-- > 0) {
      putKey(null);
    }
  }

  /** 比較オペレータを２バイトで書き込む */
  public void putComp(Comp comp) {
    try (Formatter f = new Formatter()) {
      f.format("%-2s", comp.string().toString());
      write(f.toString(), 2);
    }
  }

  /** ファイル名を１２バイトで書き込む */
  public void putFilename(Filename filename) {
    if (filename == null) {
      padding((byte)0x20, 12);
      return;
    }
    SJIS sjis = filename.getSJIS();
    if (sjis != null) {
      sjis = sjis.forceSize(12);
      write(sjis);
      return;
    }
    putRegister(filename.getRegister());
    padding((byte)0x20, 12 - 5);
  }

  /** ファイル位置種類を４バイトで書き込む */
  public void putFilePos(FilePos pos) {
    try (Formatter f = new Formatter()) {
      f.format("%-4s", pos.toString());
      write(f.toString(), 4);
    }
  }

  /** フル桁入力時アクションを書き込む */
  public void putFullAction(FullAction act) {
    putInt(act.ordinal(), 1);
  }

  /** 指定数の詰め物をする */
  public void padding(byte c, int size) {
    write(new SJIS(size, c));
  }

  /** SJISを書き込む */
  public void putSJIS(SJIS sjis) {
    write(sjis);
  }

  /** コマンドを指定して行を終了。
   * 行サイズとコマンドを出力した後、pending状態の行を出力 */
  public void endOfLine(Cmd cmd) {
    write(CRLF);

    byte[]bytes = pending.toByteArray();
    pending = null;

    try {
      try (Formatter f = new Formatter()) {
        f.format("%03d", 3 + cmd.head().length() + bytes.length);

        out.write(f.toString().getBytes());
      }
      out.write(cmd.head().getBytes());
      out.write(bytes);

      position += 3 + cmd.head().length() + bytes.length;
    } catch (IOException ex) {
      throw new WriteException("書き込めません", ex);
    }
  }
}
