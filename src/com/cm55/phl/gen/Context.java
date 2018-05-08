// Created by Cryptomedia Co., Ltd. 2006/06/10
package com.cm55.phl.gen;

import java.util.*;

import com.cm55.phl.*;
import com.cm55.phl.Command.*;
import com.cm55.phl.PHL.*;

/**
 * マクロ展開コンテキスト
 * <ul>
 * <li>マクロに渡され、そのマクロの展開結果の複数のコマンドを登録する。
 * <li>ループを持つマクロでは、そのループ情報が登録される。これは下位のマクロで
 * 直近のループを取得し、Break/Continueの処理を行うのに必要となる。
 * <li>レジスタの割当て・未割当て状態を管理する。
 * </p>
 */
public  class Context {

  /** プロファイル */
  public Profile profile;

  /** コマンドリスト */
  private ArrayList<Command>commands;

  /** ループスタック */
  private ArrayList<Loop>loops;

  /** 未割当てレジスタ */
  private EnumMap<Type,EnumSet<Register>>freeRegs;

  /** コンテキスト作成 */
  public Context(Profile profile) {
    this.profile = profile;
    commands = new ArrayList<Command>();
    loops = new ArrayList<Loop>();
    freeRegs = new EnumMap<Type, EnumSet<Register>>(Type.class);
    freeRegs.put(Type.STRING,    EnumSet.copyOf(Register.datSet));
    freeRegs.put(Type.INTEGER, EnumSet.copyOf(Register.intSet));
    freeRegs.put(Type.FLOAT,   EnumSet.copyOf(Register.fltSet));
  }

  /** オブジェクトの処理を行う。
   * コマンドであれば自身のコマンド列に格納する。
   * マクロであれば自身を指定して展開処理をさせる。 */
  public void proc(Object...elementList) {

    for (Object element: elementList) {

      if (element instanceof Command) {
        commands.add((Command)element);
        continue;
      }

      if (element instanceof Macro) {
        ((Macro)element).expand(this);
        continue;
      }

      throw new GenerateException("サポートしていないオブジェクトです：" + element.getClass());
    }
  }

  /** 格納されたコマンドリストを取得 */
  public ArrayList<Command>getCommands() {
    return commands;
  }

  /////////////////////////////////////////////////////////////////////////////
  // ループ管理
  /////////////////////////////////////////////////////////////////////////////

  /**
   * ループ情報
   */
  public class Loop {

    /** 明示的に貼られたラベル。Javaの「loop: while(true) {}」のようなもの */
    private Label explicitLabel;

    /** 自動生成された入り口ラベル */
    private Label entryLabel;

    /** 自動生成された出口ラベル */
    private Label exitLabel;

    public Label explicitLabel() { return explicitLabel; }
    public Label entryLabel() { return entryLabel; }
    public Label exitLabel() { return exitLabel; }
  }

  /** ループを追加する */
  public Loop createLoop() {
    Loop loop = new Loop();
    loop.entryLabel = new Label();
    loop.exitLabel = new Label();

    // 現在のコマンド列の最後がラベルであれば、ループに明示的なラベルが
    // 貼られたものと見なす。
    if (commands.size() > 0) {
      Command last = commands.get(commands.size() - 1);
      if (last instanceof Label)
        loop.explicitLabel = (Label)last;
    }

    // ループスタックにつむ
    loops.add(loop);

    return loop;
  }

  /** 最後のループを削除する */
  public void removeLoop() {
    loops.remove(loops.size() - 1);
  }

  /** 直近のループを取得する */
  public Loop getRecentLoop() {
    if (loops.size() == 0)
      throw new GenerateException("ループがありません");
    return loops.get(loops.size() - 1);
  }

  /** 明示的ラベルのついたループを探す */
  public Loop getLabeledLoop(Label explicitLabel) {
    for (int i = loops.size() - 1; i >= 0; i--) {
      Loop loop = loops.get(i);
      if (loop.explicitLabel == explicitLabel) return loop;
    }
    throw new GenerateException("ラベル付ループがありません");
  }

  /////////////////////////////////////////////////////////////////////////////
  // レジスタ管理
  /////////////////////////////////////////////////////////////////////////////

  /** 文字列レジスタを取得する */
  public Register strReg() {
    return allocReg(Type.STRING);
  }

  /** 整数レジスタを取得する */
  public Register intReg() {
    return allocReg(Type.INTEGER);
  }

  /** 浮動小数点レジスタを取得する */
  public Register fltReg() {
    return allocReg(Type.FLOAT);
  }

  /** レジスタ全般を取得する */
  public Register allocReg(Type type) {
    EnumSet<Register>unallocSet = freeRegs.get(type);
    Iterator<Register> it = unallocSet.iterator();
    assert(it.hasNext());
    Register reg = it.next();
    it.remove();
    return reg;
  }

  /** レジスタを開放する */
  public void releaseReg(Register... regs) {
    for (Register reg: regs) {
      if (reg == null) continue;
      EnumSet<Register>regSet = freeRegs.get(reg.type());
      assert(!regSet.contains(reg));
      regSet.add(reg);
    }
  }

  /** すべてのレジスタが自由状態であることを確認。デバッグ用 */
  public boolean allRegsFree() {
    return
      freeRegs.get(Type.STRING).size()    == Register.datSet.size() &&
      freeRegs.get(Type.INTEGER).size() == Register.intSet.size() &&
      freeRegs.get(Type.FLOAT).size()   == Register.fltSet.size();
  }
}
