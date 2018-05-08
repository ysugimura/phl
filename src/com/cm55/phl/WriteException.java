// Created by Cryptomedia Co., Ltd. 2006/06/11
package com.cm55.phl;

public class WriteException extends RuntimeException {
  public WriteException() {

  }
  public WriteException(String s) {
    super(s);
  }
  public WriteException(String s, Throwable ex) {
    super(s, ex);
  }
}
