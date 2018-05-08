// Created by Cryptomedia Co., Ltd. 2006/06/09
package com.cm55.phl;

import java.io.*;
import java.util.*;

public class Utils {

  public static  byte[]getWholeBytes(InputStream in) throws IOException {
    int BUFSIZE = 10000;
    ArrayList<byte[]>byteList = new ArrayList<byte[]>();
    while (true) {
      byte[]buffer = new byte[BUFSIZE];
      int size = in.read(buffer);
      if (size < buffer.length) {
        if (size < 0) size = 0;
        int wholeSize = byteList.size() * BUFSIZE + size;
        byte[]bytes = new byte[wholeSize];
        int index = 0;
        for (byte[]b: byteList) {
          System.arraycopy(b, 0, bytes, index, BUFSIZE);
          index += BUFSIZE;
        }
        System.arraycopy(buffer, 0, bytes, index, size);
        return bytes;
      }
      byteList.add(buffer);
    }
  }

  /** よいこはまねしない */
  @SuppressWarnings("unchecked")
  public static <K,V>Map<K,V>getMap(
      Class<K>keyClass, Class<V>valueClass, Object...objects) {
    Map<K,V>map = new HashMap<K,V>();
    assert(objects.length % 2 == 0);
    for (int i = 0; i < objects.length; i += 2) {
      assert(keyClass.isInstance(objects[i + 0]));
      assert(valueClass.isInstance(objects[i + 1]));
      map.put((K)objects[i + 0], (V)objects[i + 1]);
    }
    return map;
  }

}
