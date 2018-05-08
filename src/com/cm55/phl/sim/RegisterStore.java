// Created by Cryptomedia Co., Ltd. 2006/06/09
package com.cm55.phl.sim;

import java.text.*;
import java.util.*;

import com.cm55.phl.*;
import com.cm55.phl.PHL.*;

/**
 * レジスタ値ストア
 */
public class RegisterStore extends Listenable {

  protected EnumMap<Register,Object>valueMap =
    new EnumMap<Register,Object>(Register.class);

  /** ユーザレジスタの初期化 */
  public void initValue(Register register) {
    assert(!register.system());
    valueMap.remove(register);
  }

  /** システムレジスタの初期化 */
  public void initSystem(Register register) {
    assert(register.system());
    valueMap.remove(register);
  }

  /** ユーザレジスタへ設定 */
  public void setValue(Register register, Object value) {
    assert(!register.system());
    doSetValue(register, value);
  }

  /** システムレジスタへ設定 */
  public void setSystem(Register register, Object value) {
    assert(register.system());
    doSetValue(register, value);
  }

  /** 値の設定 */
  protected void doSetValue(Register register, Object value) {
    if (value instanceof String) {
      // 文字列の場合は設定先変数の型に変換
      value = register.type().convert(value);
    } else {
      // 文字列でない場合
      // その値は設定先変数の型と一致していなくてはいけない。
      switch (register.type()) {
      case STRING: assert(value instanceof SJIS); break;
      case INTEGER: assert(value instanceof Integer); break;
      case FLOAT: assert(value instanceof Float); break;
      }
    }
    if (value == null) valueMap.remove(register);
    else               valueMap.put(register, value);

    fireChanged(register);
  }


  /** 変数の値を取得する */
  @SuppressWarnings("unchecked")
  public <T> T getValue(Register register) {

    if (register.system()) {
      T object = getSystemValue(register);
      if (object != null) return object;
    }

    Object object = valueMap.get(register);
    if (object == null) {
      switch (register.type()) {
      case STRING: return (T)new SJIS(0);
      case INTEGER: return (T)new Integer(0);
      case FLOAT: return (T)new Float(0);
      default:
        assert(false);
      }
    }
    return (T)object;
  }

  /** 日付レジスタフォーマット */
  private static final EnumMap<Register,String>dateFormats = new EnumMap<Register,String>(
      Utils.getMap(Register.class, String.class,
          Register.DATE1, "yyyy/MM/dd",
          Register.DATE2, "yyyyMMdd",
          Register.DATE3, "yyyy/MM",
          Register.DATE4, "yyyyMM",
          Register.DATE5, "MM/dd",
          Register.DATE6, "MMdd",
          Register.DATE7, "dd/MM/yyyy",
          Register.DATE8, "ddMMyyyy",
          Register.DATE9, "MM/yyyy",
          Register.DATEA, "MMyyyy",
          Register.DATEB, "dd/MM",
          Register.DATEC, "ddMM",
          Register.DATED, "yyMMdd"
      )
  );

  /** 時刻レジスタフォーマット */
  private static final EnumMap<Register,String>timeFormats = new EnumMap<Register,String>(
      Utils.getMap(Register.class, String.class,
          Register.TIME1, "HH:mm:ss",
          Register.TIME2, "HHmmss",
          Register.TIME3, "HH:mm",
          Register.TIME4, "HHmm"
      )
  );

  /** システム変数の値を取得する */
  @SuppressWarnings("unchecked")
  protected <T> T getSystemValue(Register register) {
    EnumMap<Register,String>a;

    // DATE?, TIME?
    if (Register.dateTimeSet.contains(register)) {
      String format = dateFormats.get(register);
      if (format == null) format = timeFormats.get(register);
      if (format == null) throw new SimulateException();
      T result = (T)new SimpleDateFormat(format).format(new Date());
      return result;
    }

    // BST?
    if (Register.bstSet.contains(register)) {
      return (T)new Integer(1);
    }

    // BRCOD
    if (register == Register.BRCOD) {
      if (!valueMap.containsKey(register))
        return (T)new Integer(3); // WPC?
    }

    return null;
  }

  protected void fireChanged(Register register) {
    for (Listener l: getListeners(new Listener[0])) {
      l.changed(register);
    }
  }

  public interface Listener {
    void changed(Register register);
  }
}
