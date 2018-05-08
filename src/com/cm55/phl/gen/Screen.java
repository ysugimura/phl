// Created by Cryptomedia Co., Ltd. 2006/06/08
package com.cm55.phl.gen;

import java.util.*;

import com.cm55.phl.*;
import com.cm55.phl.Command.*;
import com.cm55.phl.PHL.*;
import com.cm55.phl.gen.Macro.*;
import com.cm55.phl.gen.Table.*;

/**
 * 画面上のフィールドの定義
 * <p>
 * 画面上に表示するフィールド(ScrField)を定義する。ScrFieldにおいて表示・入力される
 * 値は、TblFieldに格納されているものとする。つまり、ScrFieldにて入力される値は
 * 対応するTblFieldに自動的に格納されるし、ScrFieldに表示される値は対応するTblField
 * から取得されたものである。
 * </p>
 * <p>
 * ScrFieldの属性としては以下のものがある。
 * </p>
 * <ul>
 * <li>x,y座標
 * <li>枠として表示する文字列。例えば"[  ]"など、表示・入力領域を表すための
 * 文字列。なくても良い。
 * <li>値の入力・表示領域のx座標オフセット。例えば、枠として"[  ]"をx座標から表示
 * した場合、入力・表示領域はx + 1とする。この場合はオフセットは1になる。
 * <li>対応するTblField
 * <li>値のタイプ。入力用。TblFieldには文字列として格納されなければならないが、
 * 入力時には、整数、浮動小数点数として入力されるべき場合がある。
 * </ul>
 * <p>
 * また、Screenには表示・入力時に使用されるレジスタを指定する。テキスト用、
 * 整数用、浮動小数点用が必要となる。
 * </p>
 */
public class Screen extends MacroObject {

  // 値のタイプ
  private static final int TYPE_INTEGER = 0;
  private static final int TYPE_FLOAT   = 1;
  private static final int TYPE_STRING  = 2;
  private static final int TYPE_BARCODE = 3;

  /** フィールド */
  protected ScrField[]fields;

  /** デフォルトのフル桁入力時アクション */
  protected FullAction defaultFullAction = FullAction.IMMEDIATE;

  protected Screen() {
  }

  public void setFields(ScrField[]fields) {
    assert(this.fields == null && fields != null);
    this.fields = fields;
    for (ScrField field: fields) {
      field.screen = this;
    }
  }

  /** デフォルトのフル桁入力時アクションを設定する */
  @SuppressWarnings("unchecked")
  public <T extends Screen> T setDefaultFullAction(FullAction act) {
    defaultFullAction = act;
    return (T)this;
  }

  public void allocReg(Context ctx) {
  }

  public void releaseReg(Context ctx) {
  }

  /** 全フィールド内容を表示する要素を作成 */
  public Object displayAllElement() {
    Object[]objects = new Object[fields.length];
    for (int i = 0; i < fields.length; i++) {
      objects[i] = fields[i].displayElement();
    }
    return new Compound(objects);
  }

  /////////////////////////////////////////////////////////////////////////////
  // フィールド
  /////////////////////////////////////////////////////////////////////////////

  public static class ScrField {
     protected Screen screen;

     /** 表示領域をクリアする */
     public Object clearElement() { assert(false);  return null; }

     /** 値を表示する */
     public Object displayElement() { assert(false); return null; }

     /** 枠を表示し、レジスターに値を入力する。
      * ただし、specialに示されたキーが入力された場合は指定されたラベルにジャンプする。 */
     public Macro inputElement(final EnumSet<Key>special, Label label) {
       assert(false);
       return null;
     }

     public Macro inputElement(final EnumSet<Key>special, Object object) {
       assert(false);
       return null;
     }
     public Macro inputElement(final EnumSet<Key>special, final Object[]cases, final Object def) {
       assert(false);
       return null;
     }
  }

  public static class AbstractScrField extends ScrField {

    public int x;
    public int y;
    public SJIS frame;
    public int offset;
    public TblField recField;
    protected int type;

    protected AbstractScrField(int y, int x, String frame, int offset, TblField recField, int type) {
      this.x = x;
      this.y = y;
      this.frame = new SJIS(frame);
      this.offset = offset;
      this.recField = recField;
      this.type = type;
    }

    protected FullAction getFullAction() {
      return screen.defaultFullAction;
    }

    /** 表示をクリアする要素を作成 */
    @Override
    public Object clearElement() {
      if (frame != null)
        return new DisplayPartClear(y, x, frame.length());
      else
        return new DisplayPartClear(y, x, offset + recField.size);
    }

    /** 値を表示する要素を作成 */
    @Override
    public Object displayElement() {
      Compound block = new Compound();

      // フレームがあればそれを表示
      if (frame != null) {
        block.add(
            new DisplayString(y, x, frame)
        );
      }

      // 値を表示する要素を作成
      block.add(recField.displayElement(y, x + offset));

      return block;
    }

    /** 枠を表示し、レジスターに値を入力する。
     * ただし、specialに示されたキーが入力された場合は指定されたラベルにジャンプする。 */
    @Override
    public Macro inputElement(final EnumSet<Key>special, Label label) {
      return inputElement(
          special,
          new Object[] {},
          new Jump(label)
      );
    }

    /** 枠を表示し、レジスターに値を入力する。
     * ただし、specialに示されたキーが入力された場合は指定されたオブジェクトを実行する */
    @Override
    public Macro inputElement(final EnumSet<Key>special, Object object) {
      return inputElement(
          special,
          new Object[] {},
          object
      );
    }

    /** 枠を表示し、レジスターに値を入力する。
     * ただし、specialに示されたキーが入力された場合は、キーの値に応じて
     * casesを実行する。casesに示されないキーの場合にはdefを実行する */
    @Override
    public Macro inputElement(final EnumSet<Key>special, final Object[]cases, final Object def) {
      return new Macro() { protected void expand(Context ctx) {

        // フレーム表示
        if (frame != null) {
          ctx.proc( new DisplayString(y, x, frame));
        }

        // 入力
        Register strVar = ctx.strReg();
        Register intVar = null;
        Register fltVar = null;
        Compound inputBlock = null;
        switch (type) {
        case TYPE_INTEGER:
          intVar = ctx.intReg();
          inputBlock = new Compound(
              new EchoedInput(y, x + offset, intVar, recField.size, special)
              .setFullAction(getFullAction())
          );
          break;
        case TYPE_FLOAT:
          fltVar = ctx.fltReg();
          inputBlock = new Compound(
              new EchoedInput(y, x + offset, fltVar, recField.size, special)
              .setFullAction(getFullAction())
          );
          break;
        case TYPE_BARCODE:
          inputBlock = new Compound(
              new BarcodeInput(y, x + offset, strVar, recField.size, special).
                setFullAction(FullAction.IMMEDIATE)
          );
          break;
        case TYPE_STRING:
          inputBlock = new Compound(
              new EchoedInput(y, x + offset, strVar, recField.size, special)
              .setFullAction(getFullAction())
          );
          break;
        default:
          assert(false);
        }

        // 入力処理
        // 特殊キーが押された場合、ラベルにジャンプ
        // １桁以上の入力+ENTの場合break
        ctx.proc( new InfiniteLoop(
            inputBlock,
//            new JumpIf(Register.RSLT, Comp.NE, 0, label),
            new If(Register.RSLT, Comp.NE, 0,
                new Case(Register.RSLT, cases, def)
            ),
            new IfBreak(Register.NUMBR, Comp.GT, 0)
        ));

        // 特殊キーチェック、ジャンプ
        //ctx.proc( new JumpIf(Register.RSLT, Comp.NE, 0, label));

        // 入力用レジスタ使用時、変換
        switch (type) {
        case TYPE_INTEGER:
          ctx.proc( new Assign(strVar, intVar));
          break;
        case TYPE_FLOAT:
          ctx.proc( new Assign(strVar, fltVar));
          break;
        case TYPE_BARCODE:
        case TYPE_STRING:
          // nop
          break;
        default:
          assert(false);
        }

        // レコードフィールドに格納
        ctx.proc( recField.setValueElement(strVar));

        // レジスタ開放
        ctx.releaseReg(strVar, intVar, fltVar);
      }};
    }
  }

  /** 文字列用 */
  public static class StrScrField extends AbstractScrField {
    public StrScrField(int y, int x, String frame, int offset, TblField recField) {
      super(y, x, frame, offset, recField, TYPE_STRING);
    }
  }

  /** 整数用 */
  public static class IntScrField extends AbstractScrField {
    public IntScrField(int y, int x, String frame, int offset, TblField recField) {
      super(y, x, frame, offset, recField, TYPE_INTEGER);
    }
  }

  /** 浮動小数点用 */
  public static class FloatScrField extends AbstractScrField {
    public FloatScrField(int y, int x, String frame, int offset, TblField recField) {
      super(y, x, frame, offset, recField, TYPE_FLOAT);
    }
  }

  /** バーコード用 */
  public static class BarScrField extends AbstractScrField {
    public BarScrField(int y, int x, String frame, int offset, TblField recField) {
      super(y, x, frame, offset, recField, TYPE_BARCODE);
    }
  }
}
