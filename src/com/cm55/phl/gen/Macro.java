// Created by Cryptomedia Co., Ltd. 2006/06/07
package com.cm55.phl.gen;

import java.util.*;

import com.cm55.phl.*;
import com.cm55.phl.Command.*;
import com.cm55.phl.PHL.*;
import com.cm55.phl.gen.Context.*;

/**
 * マクロ
 * <p>
 * マクロは、ただ一つexpandメソッドを持つ。
 * このメソッド内にてContextを使用して複数のコマンド列を生成する。
 * </p>
 * <p>
 * マクロはImmutableであるべき。一つのマクロオブジェクトは何度expandが呼び出されても
 * 生成コマンド列は同一であるべきである。
 * </p>
 */
public abstract class Macro  {

  /** マクロを展開する */
  protected abstract void expand(Context ctx);

  /** 要素がブロックであればそのまま、そうでなければブロックに入れる */
  protected static Compound ensureBlock(Object element, boolean allowNull) {

    // nullの場合
    if (element == null) {
      if (allowNull) return null;
      return new Compound();
    }

    if (element instanceof Compound) return (Compound)element;

    assert(
        element instanceof Macro ||
        element instanceof Command ||
        element instanceof Register
    );

    return new Compound(element);
  }

  protected Compound ensureBlock(Object element) {
    return ensureBlock(element, false);
  }

  /////////////////////////////////////////////////////////////////////////////
  //
  /////////////////////////////////////////////////////////////////////////////

  /** 複合要素 */
  public static class Compound extends Macro {

    protected ArrayList<Object>elements = new ArrayList<Object>();

    /** 作成 */
    public Compound(Object ... elements) {
      add(elements);
    }

    /** 要素を加える */
    public void add(Object ... elements) {
      for (Object element: elements) {
        assert(element != null);
        this.elements.add(element);
      }
    }

    /** 展開 */
    @Override
    public void expand(Context ctx) {
      ctx.proc(elements.toArray());
    }
  }


  /** if文 */
  public static class If extends Macro {

    protected Register left;
    protected Comp comp;
    protected Object right;
    protected Compound thenClause;
    protected Compound elseClause;

    public If(Register left, Object right, Object thenClause) {
      this(left, Comp.EQ, right, thenClause);
    }

    /** 作成する */
    public If(Register left, Comp comp, Object right,
        Object thenClause) {
      this(left, comp, right, thenClause, null);
    }

    public If(Register left, Object right,
        Object thenClause, Object elseClause) {
      this(left, Comp.EQ, right, thenClause, elseClause);
    }

    /** 作成する */
    public If(Register left, Comp comp, Object right,
        Object thenClause, Object elseClause) {
      this.left = left;
      this.comp = comp;
      this.right = right;
      this.thenClause = ensureBlock(thenClause, true);
      this.elseClause = ensureBlock(elseClause, true);
    }

    /** コマンド取得 */
    @Override
    public void expand(Context ctx) {
      if (thenClause == null && elseClause == null)
        return;

      // then部のみ
      if (elseClause == null) {
        Label exitLabel = new Label();
        ctx.proc(new JumpIf(left, comp.opposite(), right, exitLabel));
        thenClause.expand(ctx);
        ctx.proc(exitLabel);
        return;
      }

      // else部のみ
      if (thenClause == null) {
        Label exitLabel = new Label();
        ctx.proc(new JumpIf(left, comp, right, exitLabel));
        elseClause.expand(ctx);
        ctx.proc(exitLabel);
        return;
      }

      // then/elseあり
      Label elseLabel = new Label();
      Label exitLabel = new Label();
      ctx.proc(new JumpIf(left, comp.opposite(), right, elseLabel));
      thenClause.expand(ctx);
      ctx.proc(new Jump(exitLabel));
      ctx.proc(elseLabel);
      elseClause.expand(ctx);
      ctx.proc(exitLabel);
    }
  }

  /** 無限ループ */
  public static class InfiniteLoop extends Compound {

    public InfiniteLoop(Object...elements) {
      super(elements);
    }

    /** マクロ展開 */
    @Override
    public void expand(Context ctx) {
      Loop loop = ctx.createLoop();
      try {
        ctx.proc(loop.entryLabel());
        super.expand(ctx);
        ctx.proc(new Jump(loop.entryLabel()));
        ctx.proc(loop.exitLabel());
      } finally {
        ctx.removeLoop();
      }
    }
  }


  /** スイッチ。Javaのswitchとは異なり、breakは必要ない。 */
  public static class Case extends Macro {

    /** スイッチ用変数名 */
    protected Register register;

    /** ケース数 */
    protected int count;

    /** スイッチキー */
    protected Integer[]keys;

    /** 実行要素 */
    protected Compound[]elements;

    /** デフォルト要素 */
    protected Compound defaultElement;

    public Case(Register register, Object[]objects) {
      this(register, objects, null);

    }

    /** 作成する */
    public Case(Register register, Object[]objects, Object defaultElement) {

      // レジスタ
      this.register = register;
      assert(register.type() == Type.INTEGER);

      // 引数
      assert(objects.length % 2 == 0);
      count = objects.length / 2;
      keys = new Integer[count];

      elements = new Compound[count];

      int index = 0;
      for (int i = 0; i < count; i++) {
        Object key = objects[index + 0];
        if (key instanceof Key) keys[i] = ((Key)key).code();
        else                     keys[i] = (Integer)key;
        Object second = objects[index + 1];
        elements[i] = ensureBlock(second, false);
        index += 2;
      }
      this.defaultElement = ensureBlock(defaultElement, true);
    }

    /** マクロ展開 */
    @Override
    public void expand(Context ctx) {

      // ラベルを作成
      Label[]caseLabels = new Label[count];
      for (int i = 0; i < caseLabels.length; i++)
        caseLabels[i] = new Label();
      Label defaultLabel = null;
      if (defaultElement != null) defaultLabel = new Label();
      Label exitLabel = new Label();

      // キーとの比較を作成
      for (int i = 0; i < elements.length; i++) {
        ctx.proc(new JumpIf(register, Comp.EQ, keys[i], caseLabels[i]));
      }

      // キーと一致しなかった場合、デフォルトがあればそこへ、なければ出口へ
      if (defaultElement != null)
        ctx.proc(new Jump(defaultLabel));
      else
        ctx.proc(new Jump(exitLabel));

      // ラベルとともに要素をおく
      for (int i = 0; i < elements.length; i++) {
        ctx.proc(caseLabels[i]);
        elements[i].expand(ctx);
        ctx.proc(new Jump(exitLabel));
      }

      // デフォルト要素を置く
      if (defaultElement != null) {
        ctx.proc(defaultLabel);
        defaultElement.expand(ctx);
        ctx.proc(new Jump(exitLabel));
      }

      // 出口ラベルをおく
      ctx.proc(exitLabel);
    }
  }

  /** 画面をクリアし、メニューを表示する */
  public static class MenuLines extends Macro {

    /** メニュー行 */
    protected Object[]lines;

    public MenuLines(Object[]lines) {
      this.lines = lines;
    }

    @Override
    public void expand(Context ctx) {
      ctx.proc(new DisplayClear());
      int line = 1;
      for (int i = 0; i < lines.length; i++) {
        Object object = lines[i];

        SJIS sjis = null;
        Register register = null;

        if (object instanceof SJIS) sjis = (SJIS)object;
        else if (object instanceof String) sjis = new SJIS((String)object);
        else if (object instanceof SJIS_DAT) {
          sjis = ((SJIS_DAT)object).getSJIS();
          if (sjis == null) register = ((SJIS_DAT)object).getRegister();
        } else if (object instanceof Register) {
          register = (Register)object;
        }

        if (sjis != null) {
          ctx.proc(new DisplayString(line, 0, sjis));
        } else {
          ctx.proc(new DisplayRegister(line, 0, register, 0, 16));
        }
        line += 2;
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // メニュー
  ////////////////////////////////////////////////////////////////////////////

  /**
   * メニューマクロ
   * <p>
   * メニューとして表示する行の配列を渡す。画面消去の後、それらは順に1,3,5,7行目に表
   * 示される。表示後にエコーなし入力が行われ、結果をINT1レジスタに受け取る。
   * この値が、'1','2'...の時に、対応するコマンドを実行する。ただし、コマンドが
   * nullの場合はスキップし、次の行が'1'に対応する。
   * </p>
   * <p>
   * スクリーンの行数を４とする。４を越える場合は複数スクリーンに分ける。
   * スクリーン間の遷移はprevKeyで前画面、nextKeyで後画面だが、ただし
   * 開始画面でのprevKeyはループを抜け、最終画面でのnextKeyは最終画面の繰返し。
   * </p>
   */
  public static class MenuLoop extends Macro {

    private static final int LINE_COUNT = 4;

    /** メニュー表示行 */
    protected SJIS[]lines;

    /** 実行要素 */
    protected Compound[]elements;

    /** 次キー */
    protected Key nextKey;

    /** 出口キー・前画面兼用 */
    protected Key prevKey;

    /** 作成する */
    public MenuLoop(String[]lines, Object[]elements, Key nextKey, Key exitKey) {
      assert(lines.length == elements.length);

      // 表示行取得
      this.lines = new SJIS[lines.length];
      for (int i = 0; i < lines.length; i++)
        this.lines[i] = new SJIS(lines[i]);

      // 実行要素取得
      this.elements = new Compound[elements.length];
      for (int i = 0; i < elements.length; i++)
        this.elements[i] = ensureBlock(elements[i], true);

      // キー定義
      this.nextKey = nextKey;
      this.prevKey = exitKey;
    }

    /** コマンドを取得する */
    @Override
    public void expand(Context ctx) {

      Loop loop = ctx.createLoop();
      ctx.proc(loop.entryLabel());

      // スクリーン数と各スクリーンエントリラベル
      int numScreen = (lines.length + LINE_COUNT - 1) / LINE_COUNT;
      Label[]labels = new Label[numScreen + 1];
      for (int i = 0; i < labels.length; i++)
        labels[i] = new Label();

      // スクリーン処理
      for (int sno = 0; sno < numScreen; sno++) {

        // エントリラベルをおく
        ctx.proc(labels[sno]);

        // 各スクリーン処理
        Label prevLabel = sno == 0? loop.exitLabel():labels[sno - 1];
        createScreen(ctx, sno * LINE_COUNT, prevLabel, sno == numScreen - 1);
      }

      // 出口ラベルをおく
      ctx.proc(loop.exitLabel());
    }

    /**
     * スクリーン作成
     * prevKey押下時はprevLabelへジャンプ
     * nextKey押下時は処理の最下部へジャンプ。ただし、最終スクリーンの場合は
     * 処理の繰返し。
     */
    protected void createScreen(Context ctx, int elemIdx, Label prevLabel, boolean endScreen) {

      Register register = ctx.intReg();
      try {
        Label topLabel = new Label();
        Label bottomLabel = new Label();
        ctx.proc(topLabel);

        int lineCount = Math.min(LINE_COUNT, lines.length - elemIdx);
        Label[]elementLabels = new Label[lineCount];

        // 画面クリア
        ctx.proc(new DisplayClear());

        // 文字列表示
        int line = 1;
        for (int i = 0; i < lineCount; i++) {
          ctx.proc(new DisplayString(line, 0, lines[elemIdx + i]));
          line += 2;
        }

        // エコー無し入力
        ctx.proc(new NoEchoInput(register));

        // キーとの比較を作成
        int keyValue = '1';
        for (int i = 0; i < lineCount; i++) {
          if (elements[elemIdx + i] == null) continue;
          elementLabels[i] = new Label();
          ctx.proc(new JumpIf(register, Comp.EQ, new Integer(keyValue), elementLabels[i]));
          keyValue++;
        }

        // 前キー
        if (prevKey != null) {
          ctx.proc(new JumpIf(register, Comp.EQ, prevKey.code(), prevLabel));
        }

        // 次キー
        if (nextKey != null) {
          if (endScreen)
            ctx.proc(new JumpIf(register, Comp.EQ, nextKey.code(), topLabel));
          else
            ctx.proc(new JumpIf(register, Comp.EQ, nextKey.code(), bottomLabel));
        }

        // 数字キー、出口キーにも該当しないとき、トップに戻る
        ctx.proc(new Jump(topLabel));

        // ラベルとともに要素をおく
        for (int i = 0; i < lineCount; i++) {
          if (elements[elemIdx + i] == null) continue;
          ctx.proc(elementLabels[i]);
          elements[elemIdx + i].expand(ctx);
          ctx.proc(new Jump(topLabel));
        }

        ctx.proc(bottomLabel);

      } finally {
        ctx.releaseReg(register);
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Break/Continue
  /////////////////////////////////////////////////////////////////////////////

  /** Break/Continue共通処理 */
  protected abstract static class BreakContinue extends Macro {

    protected Label explicitLabel;

    protected BreakContinue(Label explicitLabel) {
      this.explicitLabel = explicitLabel;
    }

    /** 入り口ラベルを取得する */
    protected Label getEntryLabel(Context ctx) {
      if (explicitLabel == null) {
        return ctx.getRecentLoop().entryLabel();
      }
      Loop loop = ctx.getLabeledLoop(explicitLabel);
      return loop.entryLabel();
    }

    /** 出口ラベルを取得する */
    protected Label getExitLabel(Context ctx) {
      if (explicitLabel == null) {
        return ctx.getRecentLoop().exitLabel();
      }
      Loop loop = ctx.getLabeledLoop(explicitLabel);
      return loop.exitLabel();
    }
  }

  /** ループを出る */
  public static class Break extends BreakContinue {

    public Break() {
      this(null);
    }
    public Break(Label label) {
      super(label);
    }
    @Override
    public void expand(Context ctx) {
      ctx.proc(new Jump(getExitLabel(ctx)));
    }
  }

  /** ループトップに戻る */
  public static class Continue extends BreakContinue {
    public Continue() {
      this(null);
    }
    public Continue(Label label) {
      super(label);
    }

    @Override
    public void expand(Context ctx) {
      ctx.proc(new Jump(getEntryLabel(ctx)));
    }
  }

  /** 条件が成立したらLoopトップに戻る */
  public static class IfContinue extends Macro {

    protected If ifMacro;

    public IfContinue(Register variable, Object value) {
      this(variable, value, null);
    }

    public IfContinue(Register variable, Object value, Label label) {
      this(variable, Comp.EQ, value, label);
    }

    public IfContinue(Register variable, Comp comp, Object value) {
      this(variable, comp, value, null);
    }

    public IfContinue(Register variable, Comp comp, Object value, Label label) {
      ifMacro = new If(variable, comp, value, new Continue(label));
    }

    @Override
    public void expand(Context ctx) {
      ctx.proc( ifMacro);
    }
  }

  /** 条件が成立したらLoopを出る */
  public static class IfBreak extends Macro {

    protected If ifMacro;

    public IfBreak(Register variable, Object value) {
      this(variable, value, null);
    }

    public IfBreak(Register variable, Object value, Label label) {
      this(variable, Comp.EQ, value, label);
      assert(variable != null);
    }

    public IfBreak(Register variable, Comp comp, Object value) {
      this(variable, comp, value, null);
    }

    public IfBreak(Register variable, Comp comp, Object value, Label label) {
      ifMacro = new If(variable, comp, value, new Break(label));
    }

    @Override
    public void expand(Context ctx) {
      ctx.proc( ifMacro);
    }
  }

  /** ファイルが存在するか */
  public static class IfFileExists extends Macro {

    protected Filename filename;
    protected Object thenClause;
    protected Object elseClause;

    public IfFileExists(Filename filename, Object thenClause) {
      this(filename, thenClause, null);
    }

    public IfFileExists(Filename filename, Object thenClause, Object elseClause) {
      this.filename = filename;
      this.thenClause = thenClause;
      this.elseClause = elseClause;
    }

    @Override
    public void expand(Context ctx) {
      Compound element = new Compound(
          new FileExists(filename),
          new If(Register.RSLT, 0, thenClause, elseClause)
      );
      element.expand(ctx);
    }
  }


  /////////////////////////////////////////////////////////////////////////////
  // 対話型処理
  /////////////////////////////////////////////////////////////////////////////


  public abstract static class Interactive extends Macro {

    protected Register register;
    protected SJIS_DAT message;

    protected Interactive(Register register) {
      this.register = register;
      assert(Register.intSet.contains(register));
    }

    protected Object showMessageElement(int line) {
      if (message == null)
        return new Compound();
      if (message.getSJIS() != null)
        return new DisplayString(line, 0, message.getSJIS());
      return new DisplayRegister(line, 0, message.getRegister(), 0, 16);
    }

    @SuppressWarnings("unchecked")
    public <T extends Interactive> T setMessage(SJIS_DAT message) {
      this.message = message;
      return (T)this;
    }
  }

  /**
   * 対話型ファイル受信。
   * <p>
   * キー入力受付用と結果の設定用に一つのINTレジスタを用いる。
   * 正常に受信した場合は０、そうでない場合は０以外が設定される。
   * </p>
   */
  public static class InteractiveFileDownload extends Interactive {

    /** 作成する */
    public InteractiveFileDownload(Register register) {
      super(register);
    }

    /** マクロを展開する */
    @Override
    public void expand(Context ctx) {
      ctx.proc( new InfiniteLoop(
          new DisplayClear(),
          /*
          new Object() {
            Object select() {
              if (message == null) return new Block();
              if (message.getSJIS() != null)
                return new DisplayString(0, 1, message.getSJIS());
              return new DisplayRegister(0, 1, message.getRegister(), 0, 16);
            }
          }.select(),
          */
          showMessageElement(1),
          new DisplayString(3, 0, "開始しますか"),
          new DisplayString(7, 0, "ENT:Y"),
          new NoEchoInput(register),
          new If(register, Comp.NE, Key.ENT, new Compound(
              new Assign(register, -3),
              new Break()
          )),
          new DisplayClear(),
          new DisplayString(7, 0, "F1=中断"),
          new DisplayString(3, 0, "受信中"),
          new CommDownload(),
          new DisplayClear(),
          new Case(Register.RSLT, new Object[] {
              0, new Compound(
                  new DisplayString(1, 0, "終了しました"),
                  new NoEchoInput(register),
                  new Assign(register, 0)
              ),
              -1, new Compound(
                  new DisplayString(1, 0, "ﾀﾞｳﾝﾛｰﾄﾞｴﾗｰ"),
                  new NoEchoInput(register),
                  new Assign(register, -1)
              ),
              -2, new Compound(
                  new DisplayString(1, 0, "中断しました"),
                  new NoEchoInput(register),
                  new Assign(register, -2)
              )
          }),
          new DisplayClear(),
          new Break()
      ));
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // 対話型ファイル処理
  /////////////////////////////////////////////////////////////////////////////

  public abstract static class InteractiveFile extends Interactive {
    protected Filename filename;
    protected InteractiveFile(Register variable, Filename filename) {
      super(variable);
      this.filename = filename;
    }

    protected Object showFilenameElement(int line) {
      if (filename.getSJIS() != null)
        return new DisplayString(line, 0, filename.getSJIS());
      else
        return new DisplayRegister(line, 0, filename.getRegister(), 0, 12);
    }
  }

  /**
   * 対話型ファイル送信
   * <p>
   * キー入力受付用と結果の設定用に一つのINTレジスタを用いる。
   * 正常に送信した場合は０、そうでない場合は０以外が設定される。
   * </p>
   */
  public static class InteractiveFileUpload extends InteractiveFile {

    public InteractiveFileUpload(Register register, Filename filename) {
      super(register, filename);
    }

    /** マクロ展開 */
    @Override
    public void expand(Context ctx) {

      ctx.proc( new InfiniteLoop(
          new DisplayClear(),

          // ファイルの存在チェック
          new FileExists(filename),
          new If(Register.RSLT, -1, new Compound(
              new DisplayString(1, 0, "該当ﾌｧｲﾙなし"),
              new NoEchoInput(register),
              new Assign(register, -4),
              new Break()
          )),

          // 送信確認
          showMessageElement(1),
          showFilenameElement(3),
          new DisplayString(5, 0, "送信しますか"),
          new DisplayString(7, 0, "ENT:Y"),
          new NoEchoInput(register),
          new If(register, Comp.NE, Key.ENT, new Compound(
              new Assign(register, -3),
              new Break()
          )),

          // 送信
          new DisplayClear(),
          new DisplayString(7, 0, "F1=中断"),
          new DisplayString(3, 0, "送信中"),
          new CommUpload(filename),
          new DisplayClear(),
          new Case(Register.RSLT, new Object[] {
              0, new Compound(
                  new DisplayString(1, 0, "終了しました"),
                  new NoEchoInput(register),
                  new Assign(register, 0)
              ),
              -1, new Compound(
                  new DisplayString(1, 0, "ｱｯﾌﾟﾛｰﾄﾞｴﾗｰ"),
                  new NoEchoInput(register),
                  new Assign(register, -1)
              ),
              -2, new Compound(
                  new DisplayString(1, 0, "中断しました"),
                  new NoEchoInput(register),
                  new Assign(register, -2)
              )
          }),

         new DisplayClear(),
         new Break()
     ));
    }
  }

  /** ファイル削除 */
  public static class InteractiveFileDelete extends InteractiveFile {

    public InteractiveFileDelete(Register register, Filename filename) {
      super(register, filename);
    }

    @Override
    public void expand(Context ctx) {
      ctx.proc( new InfiniteLoop(
          new DisplayClear(),
          new FileExists(filename),
          new If(Register.RSLT, Comp.NE, 0, new Compound(
              new DisplayString(1, 0, "該当ﾌｧｲﾙなし"),
              new NoEchoInput(register),
              new Assign(register, -1),
              new Break()
          )),
          showMessageElement(1),
          showFilenameElement(3),
          new DisplayString(5, 0, "削除しますか"),
          new DisplayString(7, 0, "ENT:Y"),
          new NoEchoInput(register),
          new If(register, Comp.NE, Key.ENT, new Compound(
              new Assign(register, -2),
              new Break()
          )),
          new FileDelete(filename),
          new DisplayClear(),
          new If(Register.RSLT, 0,
              new Compound(
                  new DisplayString(1, 0, "削除しました"),
                  new NoEchoInput(register),
                  new Assign(register, 0)
              ),
              new Compound(
                  new DisplayString(1, 0, "削除エラー"),
                  new NoEchoInput(register),
                  new Assign(register, -1)
              )
          ),
          new Break()
      ));
    }
  }

  /** Register.RSLTはif文にしか指定できず、直接他のレジスタに値を
   * コピーすることができない。-3～0の範囲の値を任意のレジスタにコピーする */
  public static class GetRSLT extends Macro {
    protected Register register;

    public GetRSLT(Register register) {
      this.register = register;
    }

    @Override
    protected void expand(Context ctx) {
      ctx.proc(
          new Case(Register.RSLT,
              new Object[] {
                0, new Assign(register, 0),
                -1, new Assign(register, -1),
                -2, new Assign(register, -2),
            },
            new Assign(register, -3)
          )
      );
    }
  }

}
