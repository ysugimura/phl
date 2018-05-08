// Created by Cryptomedia Co., Ltd. 2006/06/09
package com.cm55.phl.sim;

import java.util.*;

import com.cm55.phl.*;

/**
 * ファイルエリア
 */
public class FileArea extends Listenable {

  /** ファイルリスト */
  protected ArrayList<MemoryFile>fileList = new ArrayList<MemoryFile>();

  /** 指定された名前のファイルを取得 */
  protected MemoryFile getFile(SJIS filename) {
    
    for (MemoryFile file: fileList) {
      if (file.filename.equals(filename)) return file;
    }
    return null;
  }

  /** ファイルに書き込み */
  protected void putFile(SJIS filename, byte[]bytes) {

    
    MemoryFile file = ensureFile(filename);
    file.fileData = bytes;
    file.fileSize = bytes.length;
    file.recordPointer = 0;
  }

  /** ファイルを取得。存在しなければ作成 */
  protected MemoryFile ensureFile(SJIS filename) {
    MemoryFile file = getFile(filename);
    if (file == null) {
      file = new MemoryFile();
      file.filename = filename;
      fileList.add(file);
    }
    return file;
  }

  /** ファイルを削除 */
  protected boolean deleteFile(SJIS filename) {
    for (int i = 0; i < fileList.size(); i++) {
      MemoryFile file = fileList.get(i);
      if (file.filename.equals(filename)) {
        fileList.remove(i);
        return true;
      }
    }
    return false;
  }

  /////////////////////////////////////////////////////////////////////////////

  /**
   * PHLのファイルを真似しているが、どこまで真似できているのかわからない。
   * とにかくこのファイルはレコード長が決まっていない、それぞれのリード・ライト
   * コマンドにおいて好き勝手にレコード長を指定できてしまうため、実際のレコード
   * の途中から読み出すことも平気でできてしまう。
   * また、レコードの最後にCRLFを付加することがAPIレベルでできるのだが、
   * これはおそらく任意長の行を作成していくことを意図しているものと思われる。
   * つまりテキストファイルである。
   */
  public class MemoryFile {

    /** ファイル名 */
    protected SJIS filename;

    /** ファイルデータ */
    protected byte[]fileData = new byte[0];

    /** レコードポインタ */
    protected int recordPointer;

    /** ファイルサイズ */
    protected int fileSize;

    /** EOF状態 */
    protected boolean eof = true;

    @Override
    public String toString() {
      return "filename:" + filename + ", size:" + fileSize + 
        ", pointer:" + recordPointer + ", eof:" + eof;
    }
    
    /** ファイルサイズを取得 */
    public int fileSize() {
      return fileSize;
    }

    /** 全ファイルバイトを取得 */
    public byte[]getBytes() {
      byte[]bytes = new byte[fileSize];
      System.arraycopy(fileData, 0, bytes, 0, fileSize);
      return bytes;
    }

    /** 現在のポインタから書込み */
    public void write(SJIS sjis) {

      int needSize = recordPointer + sjis.length();
      if (fileData.length < needSize) {
        int newSize = Math.max(needSize, fileData.length * 2);
        byte[]newData = new byte[newSize];
        System.arraycopy(fileData, 0, newData, 0, fileData.length);
        fileData = newData;
      }
      System.arraycopy(sjis.getBytes(), 0, fileData, recordPointer, sjis.length());
      fileSize = Math.max(needSize, fileSize);

      eof = false;
    }

    /** 最後に追加 */
    public void append(SJIS sjis) {
      recordPointer = fileSize;
      write(sjis);

      eof = false;
    }

    /** 前のレコードへ。
     * 前のレコードに移動できない場合、eof状態になる。
     * eof状態でカレントレコードを読み込むと読込レジスタがクリアされる。
     * レコートポインタは移動しないため、返り値をみずにoverwriteすると
     * 空白が書き込まれてしまうことに注意 */
    public boolean previous(int size) {
      if (0 <= recordPointer - size) {
        recordPointer -= size;
        eof = false;
        return true;
      }
      eof = true;
      return false;
    }

    /** 後のレコードへ */
    public boolean next(int size) {
      if (recordPointer + size <= fileSize - size) {
        recordPointer += size;
        eof = false;
        return true;
      }
      eof = true;
      return false;
    }

    /** 最初のレコードへ */
    public boolean top(int size) {
      if (size <= fileSize) {
        recordPointer = 0;
        eof = false;
        return true;
      }
      eof = true;
      return false;
    }

    /** 最後のレコードへ */
    public boolean bottom(int size) {
      if (size <= fileSize) {
        recordPointer = fileSize - size;
        eof = false;
        return true;
      }
      eof = true;
      return false;
    }

    /** 現在位置のレコードを読込。eof状態の場合は空白が返る */
    public SJIS read(int size) {

      
      if (eof) return new SJIS(size);

      if (recordPointer + size > fileSize) return null;
      

      
      return new SJIS(fileData, recordPointer, size);
    }

    /** レコード数を取得する */
    public int recordCount(int size) {
      return fileSize / size;
    }

    /** 位置付けする */
    public boolean position(int recordLen, int index) {

      int newPointer = recordLen * index;
      //ystem.err.println(" fileSize " + fileSize + "," + index + "," + (fileSize / recordLen));
      if (fileSize < newPointer + recordLen) {
        eof = true;
        return false;
      }      
      recordPointer = newPointer;
      eof = false;
      return true;
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //
  /////////////////////////////////////////////////////////////////////////////

  /** 二分検索ユーティリティ */
  static <T> int binarySearch(Slots<T> slots, T key, Comparator<T> comparator) {

    
    int low = 0;
    int high = slots.size()-1;

    while (low <= high) {
      int mid = (low + high) >> 1;
      T midVal = slots.get(mid);

      
      int cmp = comparator.compare(midVal, key);

      if (cmp < 0)
        low = mid + 1;
      else if (cmp > 0)
        high = mid - 1;
      else
        return mid; // key found
    }
    return -(low + 1);  // key not found.
  }

  public interface Slots<T> {
    int size();
    T get(int index);
  }
}
