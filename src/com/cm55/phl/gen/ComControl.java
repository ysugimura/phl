// Created by Cryptomedia Co., Ltd. 2006/07/12
package com.cm55.phl.gen;

import java.io.*;

import com.cm55.phl.PHL.*;

/**
 * 接続設定ファイル作成
 * この設定は
 */
public class ComControl {

  /** ファイル名 */
  public static final String FILENAME = "HT_COM";

  /**
   * ファイルコンテンツ。
   * デフォルト値を表す（らしい）。
   * この通信条件はPHLアプリケーションの中のアップロード・ダウンロードのみ
   * で使用される。
   *
   * 通信ポート設定はPHL1600の場合、0:RS232C, 1:IRDA, 2:光通信
   * PHL2600の場合、0:RS232C, 1:光通信
   */
  private static final byte[]TEMPLATE = new byte[] {
    0x00,
    0x01, // 通信ポート 0,1,2
    0x00,
    0x01,
    0x00,
    0x05,
    0x00,
    0x00,
    0x0D,
    0x0A,
  };

  private byte[]content;
  
  public ComControl(Machine machine) {
    content = new byte[TEMPLATE.length];
    System.arraycopy(TEMPLATE, 0, content, 0, TEMPLATE.length);
    if (machine == Machine.PHL1600)
      content[1] = 2;
    else
      content[1] = 1;
  }
  
  /** 指定ディレクトリに書き込み */
  public void write(File dir) throws IOException {
    FileOutputStream out = new FileOutputStream(new File(dir, FILENAME));
    try {
      out.write(content);
    } finally {
      out.close();
    }
  }
}
