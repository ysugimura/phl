// Created by Cryptomedia Co., Ltd. 2006/06/08
package com.cm55.phl.gen;

import java.lang.annotation.*;

import com.cm55.phl.*;
import com.cm55.phl.Command.*;
import com.cm55.phl.PHL.*;
import com.cm55.phl.gen.Macro.*;

/**
 * ファイルをハンドリングするためのユーティリティクラス
 * <p>
 * PHLのファイル関連のコマンドでは、いちいちファイル名、レコード長、入出力レジスタ
 * を指定しなければならないため、それらを一括して覚えておく。以後、この
 * オブジェクトを通してファイルの読み書きをする際にはもはや指定する必要がない。
 * </p>
 * <p>
 * さらに、フィールドについてもここで一度に定義してしまうため、以後は位置とサイズ
 * を指定する必要がなくなる。
 * </p>
 */
public class Table extends MacroObject {

  /** ファイル名称 */
  protected Filename filename;

  /** レコード長 */
  protected int recordLen;

  /** レコード入出力用変数 */
  protected Register recordReg;

  /** 各フィールド */
  protected TblField[]fields;

  /** ファイル名、レコード長、入出力レジスタを指定して作成
   * レコード長０指定のときは、全フィールドの総和で決定される。
   */
  public Table(SJIS name, int recordLen) {
    this.filename = new Filename(name);
    this.recordLen = recordLen;
  }

  public void allocReg(Context ctx) {
    this.recordReg = ctx.strReg();
  }

  public void releaseReg(Context ctx) {
    ctx.releaseReg(recordReg);
  }

  /** フィールドを設定する */
  public void setFields(TblField[]fields) {
    assert(this.fields == null);

    this.fields = fields;
    /*
    {
      int count = 0;
      for (TblField[]inFields: fieldsArray) {
        count += inFields.length;
      }
      this.fields = new TblField[count];
      int index = 0;
      for (TblField[]inFields: fieldsArray) {
        System.arraycopy(inFields, 0, this.fields, index, inFields.length);
        index += inFields.length;
      }
    }
    */


    int position = 0;
    for (TblField field: fields) {
      field.table = this;
      field.position = position;
      position += field.size;
    }
    if (recordLen == 0) this.recordLen = position;
    else assert(recordLen >= position);

  }

  /** レコードのクリアを行う要素を作成する */
  public Object recordClearElement() {
    return new StringShift(recordReg, recordLen);
  }

  /** レコード数取得の要素を作成する */
  public Object recordCountElement(Register var) {
    return new Compound(
        new RecordCount(filename, recordLen, var),
        new If(Register.RSLT, Comp.NE, 0, new Assign(var, -1))
    );
  }

  /** レコード追加を行う要素を作成する */
  public Object appendElement() {
    assert(fields != null);
    return new RecordWrite(filename, recordLen, recordReg);
  }

  /** レコード上書きを行う要素を作成する */
  public Object overwriteElement() {
    assert(fields != null);
    return new RecordWrite(filename, recordLen, recordReg, true, false);
  }

  /** 最初のレコードに移行する要素を作成する。成功の場合はstatusReg=0 */
  public Object topElement(final Register statusReg) {
    assert(fields != null);
    return new Macro() { protected void expand(Context ctx) {
      ctx.proc(
          new RecordRead(filename, recordLen, recordReg, FilePos.TOP),
          new If(Register.RSLT, 0,
              new Assign(statusReg, 0),
              new Assign(statusReg, -1)
          )
     );
    }};
  }

  /** 前のレコードに移行する要素を作成する。成功の場合はstatusReg=0。
   * ひどいことにRecordReadはEOF状態であってもレジスタをクリアしてしまう。
   * その場合にはTOPを読み直す。 */
  public Object previousElement(final Register statusReg) {
    assert(fields != null);
    return new Macro() { protected void expand(Context ctx) {
      ctx.proc(
          // 前レコード読込
          new RecordRead(filename, recordLen, recordReg, FilePos.PREV),
          // EOF状態の場合、レジスタはクリアされてしまっている
          // TOPを読み込む
          new If(Register.RSLT, -2,
              new Compound(
                  new RecordRead(filename, recordLen, recordReg, FilePos.TOP),
                  new Assign(statusReg, -1)
              ),
              new Assign(statusReg, 0)
          )
      );
    }};
  }

  /** 後のレコードに移行する要素を作成する。成功の場合はstatusReg=0 */
  public Object nextElement(final Register statusReg) {
    assert(fields != null);
    return new Macro() { protected void expand(Context ctx) {
      ctx.proc(
          // 後レコード読込
          new RecordRead(filename, recordLen, recordReg, FilePos.NEXT),
          // EOF状態の場合、レジスタはクリアされてしまっている
          // BOTを読み込む
          new If(Register.RSLT, -2,
              new Compound(
                  new RecordRead(filename, recordLen, recordReg, FilePos.BOT),
                  new Assign(statusReg, -1)
              ),
              new Assign(statusReg, 0)
          )
       );
    }};
  }

  /** 最後のレコードに移行する要素を作成する。成功の場合はstatusReg=0 */
  public Object bottomElement(final Register statusReg) {
    assert(fields != null);
    return new Macro() { protected void expand(Context ctx) {
      ctx.proc(
          new RecordRead(filename, recordLen, recordReg, FilePos.BOT),
          new If(Register.RSLT, 0,
              new Assign(statusReg, 0),
              new Assign(statusReg, -1)
          )
      );
    }};
  }

  /** 指定されたフィールドを指定されたレジスタの値で二分探索する */
  public Object searchElement(TblField field, Register keyVar) {
    assert(field.table == this);
    return new MasterSearch(
        filename,
        recordLen,
        keyVar,
        field.position,
        field.size,
        recordReg
    );
  }

  /////////////////////////////////////////////////////////////////////////////
  // フィールド
  /////////////////////////////////////////////////////////////////////////////

  /** レコードフィールド */
  public static class TblField {

    /** フィールドサイズ */
    protected int size;

    /** フィールド位置 */
    protected int position;

    /** テーブルオブジェクトを指す */
    protected Table table;

    /** フィールドサイズを指定して作成 */
    public TblField(int size) {
      this.size = size;
    }

    /** 指定位置に値を表示する要素を作成 */
    public Object displayElement(int y, int x) {
      return new DisplayRegister(y, x, table.recordReg, position, size);
    }

    /** 値を設定する要素を作成。
     * コピー元レジスタがDATでない場合は、一時的にDATを使用する。 */
    public Object setValueElement(final Register register) {
      // コピー元がDATではない
      if (!Register.datSet.contains(register)) {
        return new Macro() { protected void expand(Context ctx) {
          Register temp = ctx.strReg();
          ctx.proc(
              new Assign(temp, register),
              new ExtractCopy(
                  table.recordReg, position, temp, 0, size)
          );
          ctx.releaseReg(temp);
        }};
      }

      // コピー元がDAT
      return new ExtractCopy(
          table.recordReg, position, register, 0, size);
    }

    public Object setValueElement(final String s) {
      return setValueElement(new SJIS(s));
    }

    public Object setValueElement(final SJIS s) {
      return new Macro() { protected void expand(Context ctx) {
        Register datReg = ctx.strReg();
        ctx.proc(
            new Assign(datReg, s),
            setValueElement(datReg)
        );
        ctx.releaseReg(datReg);
      }};
    }

    /** 値を取得する要素を作成。
     * コピー先がDATでない場合は一時的にDATを使用する */
    public Object getValueElement(final Register dest) {

      // コピー元がDATではない
      if (!Register.datSet.contains(dest)) {
        return new Macro() { protected void expand(Context ctx) {
          Register temp = ctx.strReg();
          ctx.proc(
              new ExtractCopy(
                  temp, 0, table.recordReg, position, size),
              new Assign(dest, temp)
          );
          ctx.releaseReg(temp);
        }};
      }

      // コピー先がDAT
      return new Compound(
          new VariableInit(dest),
          new ExtractCopy(dest, 0, table.recordReg, position, size)
      );
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //
  /////////////////////////////////////////////////////////////////////////////

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface TBLFLD {

    /** 番号 */
    int number();

    /** サイズ */
    int size() default 0;
  }

//  public static void main(String[]args) {
//    try {
//      Field[]fields = MoveTable.class.getFields();
//      for (Field field: fields) {
//        TBLFLD fld = field.getAnnotation(TBLFLD.class);
//        if (fld == null) continue;
//        System.out.println("" + field);
//
//        System.out.println("  " + fld.size());
//      }
//    } catch (Exception ex) {
//      ex.printStackTrace();
//    }
//  }
}
