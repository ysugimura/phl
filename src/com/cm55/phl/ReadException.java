// Created by Cryptomedia Co., Ltd. 2006/06/07
package com.cm55.phl;

public class ReadException extends RuntimeException {

  public int lineNumber;

  public ReadException(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  public ReadException(int lineNumber, String s) {
    super(s);
    this.lineNumber = lineNumber;
  }

  public ReadException(int lineNumber, String s, Throwable ex) {
    super(s, ex);
    this.lineNumber = lineNumber;
  }

  public String getMessage() {
    return "" + lineNumber + ":" + super.getMessage();
  }
}
