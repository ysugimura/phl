// Created by Cryptomedia Co., Ltd. 2006/07/08
package com.cm55.phl.gen;

import java.io.*;

import com.cm55.phl.*;

/**
 * アプリケーション管理表作成
 */
public class AppControl {

  /** アプリケーション管理表の名称 */
  public static final String FILENAME = "HT_APCTL";

  /** アプリケーションの最大数 */
  public static final int MAX_APPS = 3;

  protected String[]names;
  protected Generator[]gens;
  protected int prior;

  /** ファイル名とジェネレータを指定して作成 */
  public AppControl(String name, Generator gen) {
    this(
        new String[] { name },
        new Generator[] { gen },
        0
    );
  }

  /** ファイル名、ジェネレータ、優先起動を指定して作成 */
  public AppControl(String[]names, Generator[]gens, int prior) {
    assert(
        0 < names.length &&
        names.length <= MAX_APPS &&
        names.length == gens.length &&
        0 <= prior && prior < names.length);
    this.names = names;
    this.gens = gens;
    this.prior = prior;
  }

  /** 指定ディレクトリに書き込み */
  public void write(File dir) throws IOException {
    for (int i = 0; i < names.length; i++)
      gens[i].outputHTC(new File(dir, names[i]));

    byte[]CRLF = new byte[] { (byte)0xD, (byte)0xA };
    FileOutputStream out = new FileOutputStream(new File(dir, FILENAME));
    try {
      for (int i = 0; i < MAX_APPS; i++) {
        if (i >= names.length) {
          out.write(new SJIS("A ").getBytes());
          out.write(CRLF);
          continue;
        }
        writeFilled(out, "A", 2);
        writeFilled(out, names[i], 8 + 1 + 3 + 2);
        writeFilled(out, gens[i].title.title, 20);
        out.write(CRLF);
      }
      out.write(new SJIS("B " + (prior + 1)).getBytes());
      out.write(CRLF);
    } finally {
      out.close();
    }
  }

  protected void writeFilled(OutputStream out, String s, int size) throws IOException {
    writeFilled(out, new SJIS(s), size);
  }

  protected void writeFilled(OutputStream out, SJIS sjis, int size) throws IOException {
    byte[]bytes = sjis.getBytes();
    out.write(bytes);
    if (bytes.length < size) {
      out.write(new SJIS(size - bytes.length).getBytes());
    }
  }
}
