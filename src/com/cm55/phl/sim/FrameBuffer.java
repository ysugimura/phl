// Created by Cryptomedia Co., Ltd. 2006/06/09
package com.cm55.phl.sim;

import com.cm55.phl.*;

/**
 * シミュレータフレームバッファ
 */
public class FrameBuffer extends Listenable {

  protected final int numColumns;
  protected final int numRows;

  private Cell[][]rows;
  private FBPoint cursor;

  public FrameBuffer(int numColumns, int numRows) {
    this.numColumns = numColumns;
    this.numRows = numRows;

    rows = new Cell[numRows][];
    clearAll();
  }

  public int numRows() { return numRows; }
  public int numColumns() { return numColumns; }

  public synchronized void clearAll() {
    for (int y = 0; y < numRows; y++) {
      rows[y] = new Cell[numColumns];
      for (int x = 0; x < numColumns; x++)
        rows[y][x] = SPACE;
    }

    fireChanged(0, 0, numColumns, numRows);
  }

  /** 部分消去 */
  public synchronized void clearPart(int y, int x1, int length, byte attr) {
    if (y < 0 || y >= numRows) return;
    int x2 = Math.min(numColumns, x1 + length);
    x1 = Math.max(0, x1);
    if (x1 >= x2) return;
    for (int x = x1; x < x2; x++) {
      rows[y][x] = SPACE;
    }
    fireChanged(x1, y, x2 - x1, 1);
  }

  /** 文字列描画 */
  public synchronized void drawSJIS(int y, int x, SJIS sjis, byte attr) {

    // 座標と文字列の補正
    if (y < 0 || y >= numRows) return;
    if (x < 0) {
      sjis = sjis.extract(-x);
      x = 0;
    }
    if (x + sjis.length() > numColumns) {
      sjis = sjis.extract(0, numColumns - x);
    }

    // 描画
    for (int i = 0; i < sjis.length(); i++) {
      rows[y][x + i] = new Cell(
          sjis.byteAt(i),
          (char)0,
          0,
          0,
          false
      );
    }

    //
    fireChanged(x, y, sjis.length(), 1);
  }

  /** 簡易行取得 */
  public synchronized SJIS getRow(int y) {


    byte[]row = new byte[numColumns];
    for (int i = 0; i < numColumns; i++) {
      row[i] = rows[y][i].code;
    }
    SJIS line = new SJIS(row);
    return line;
  }

  public synchronized void cursorOff() {
    FBPoint prev = cursor;
    cursor = null;

    if (prev != null &&
        0 <= prev.x  && prev.x < numColumns &&
        0 <= prev.y && prev.y < numRows)
      fireChanged(prev.x, prev.y, 1, 1);
  }

  public synchronized void cursorOn(int y, int x) {
    if (0 <= x  && x < numColumns && 0 <= y && y < numRows) {
      cursor = new FBPoint(x, y);
      fireChanged(x, y, 1, 1);
    } else {
      cursorOff();
    }
  }

  public synchronized FBPoint cursor() { return cursor; }

  /////////////////////////////////////////////////////////////////////////////
  // セル
  /////////////////////////////////////////////////////////////////////////////

  public static class Cell {
    public byte code; // SJIS文字
    public char character; // 文字
    public byte kind; // 0:ANK, 1:漢字の左, 2:漢字の右
    public byte body; // 0:1/4角、1:文字の上側、2:文字の下側
    public boolean reverse; // 反転

    public Cell(byte code, char c, int  kind,int body, boolean reverse) {
      this.code = code;
      this.character = c;
      this.kind = (byte)kind;
      this.body = (byte)body;
      this.reverse = reverse;
    }
  }

  public static final Cell SPACE = new Cell(
      (byte)0x20,
      (char)0x20,
      0,
      0,
      false
  );


  public static class FBPoint extends java.awt.Point {
    public FBPoint(int x, int y) {
      super(x, y);
    }
  }

  public static class FBRect extends java.awt.Rectangle {
    public FBRect(int x, int y, int width, int height) {
      super(x, y, width, height);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // リスナ
  /////////////////////////////////////////////////////////////////////////////

  protected void fireChanged(int x, int y, int width, int height) {
    FBRect rect = new FBRect(x, y, width, height);
    for (Listener l: getListeners(new Listener[0]))
      l.changed(rect);
  }

  public interface Listener {
    public void changed(FBRect rect);
  }


}
