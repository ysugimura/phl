// Created by Cryptomedia Co., Ltd. 2006/06/12
package com.cm55.phl.sim;

import java.util.*;

public class Listenable {

  protected ArrayList<Object> listeners = new ArrayList<Object>();

  public void addListener(Object l) {
    if (listeners.indexOf(l) >= 0) return;
    listeners.add(l);
  }

  public void removeListener(Object l) {
    int index = listeners.indexOf(l);
    if (index >= 0) listeners.remove(index);
  }

//  @SuppressWarnings("unchecked")
  protected <T>T[]getListeners(T[]a) {
    return listeners.toArray(a);
  }
}
