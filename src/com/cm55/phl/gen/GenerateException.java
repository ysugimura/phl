// Created by Cryptomedia Co., Ltd. 2006/06/13
package com.cm55.phl.gen;

import com.cm55.phl.*;

/**
 * 生成中の例外
 */
public class GenerateException extends WriteException {
  public GenerateException() {
  }
  public GenerateException(String s) {
    super(s);
  }
  public GenerateException(String s, Throwable th) {
    super(s, th);
  }
}
