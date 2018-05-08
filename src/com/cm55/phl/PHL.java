// Created by Cryptomedia Co., Ltd. 2006/06/05
package com.cm55.phl;

import java.util.*;

public class PHL {

  /** コマンド */
  public enum Cmd {
    Title("A ", Command.Title.class) // タイトル
    ,Label("B ", Command.Label.class) // ラベル
    ,Jump("J01", Command.Jump.class) // 無条件ジャンプ
    ,JumpIf("J02", Command.JumpIf.class) // 条件ジャンプ
    ,Assign("E01", Command.Assign.class) // 変数に変数値あるいは定数を設定する
    ,StringConcat("E02", Command.StringConcat.class) // 文字列どうしの連結
    ,ExtractCopy("E03", Command.ExtractCopy.class) // 抽出コピー
    ,StringShift("E04", Command.StringShift.class) // 文字列シフト
    ,NumberToString("E06", Command.NumberToString.class) // 数値から文字列への変換
    ,VariableInit("E08", Command.VariableInit.class) // 変数初期化
    ,RecordWrite("F01", Command.RecordWrite.class) // レコード書き込み
    ,RecordRead("F02", Command.RecordRead.class) // ファイル読み取り
    ,RecordCount("F03", Command.RecordCount.class) // レコード数取得
    ,FileDelete("F05", Command.FileDelete.class) // ファイル削除
    ,FileExists("F07", Command.FileExists.class) // ファイル存在チェック
    ,MasterSearch("F08", Command.MasterSearch.class) // マスタ検索
    ,EchoedInput("I01", Command.EchoedInput.class) // エコー入力
    ,BarcodeInput("I02", Command.BarcodeInput.class) // バーコード入力
    ,BarcodeInfo("I03", Command.BarcodeInfo.class) // バーコード読み取り情報
    ,NoEchoInput("I05", Command.NoEchoInput.class) // エコーなし入力
    ,DisplayString("D01", Command.DisplayString.class) // 表示
    ,DisplayClear("D03", Command.DisplayClear.class) // 表示クリア
    ,DisplayPartClear("D04", Command.DisplayPartClear.class) // 部分クリア
    ,DisplayRegister("D05", Command.DisplayRegister.class) // 変数表示
    ,CommUpload("C01", Command.CommUpload.class) // ファイルアップロード
    ,CommDownload("C02", Command.CommDownload.class) // ファイルダウンロード
    ,WaitMS("H04", Command.WaitMS.class) // ウェイト実行
    ;

    /** HTCファイル上のヘッダ */
    private SJIS head;

    public final Class<? extends Command>clazz;
    
    private Cmd(String cmd, Class<? extends Command>clazz) {
      this.head = new SJIS(cmd);
      this.clazz = clazz;
    }

    public SJIS head() {
      return head;
    }

    private static Map<SJIS,Cmd>headMap;

    /** ヘッダからCmdを取得する */
    public static Cmd findCommand(SJIS head) {
      if (headMap == null) {
        headMap = new HashMap<SJIS,Cmd>();
        for (Cmd cmd: values()) {
          headMap.put(cmd.head, cmd);
        }
      }
      return headMap.get(head);
    }
  }


  /** キー */
  public enum Key {
    Trigger("Trigger", 225),
    Q1("Q1", 226),
    Q2("Q2", 227),
    BS("BS", '\b'),
    CLR("CLR", 24),
    Zero("0", '0', true),
    One("1", '1', true),
    Two("2", '2', true),
    Three("3", '3', true),
    Four("4", '4', true),
    Five("5", '5', true),
    Six("6", '6', true),
    Seven("7", '7', true),
    Eight("8", '8', true),
    Nine("9", '9', true),
    ENT("ENT", 13),
    Period(".", 46, true),
    F1("F1", 241),
    F2("F2", 242),
    F3("F3", 243),
    F4("F4", 244),
    F5("F5", 245),
    F6("F6", 246),
    F7("F7", 247),
    F8("F8", 248);

    /** キートップ文字列 */
    private SJIS keytop;

    /** キーコード */
    private int code;

    /** 表示文字 */
    private boolean hasGlyph;

    private Key(String keytop, int code) {
      this(keytop, code, false);
    }

    private Key(String keytop, int code, boolean hasGlyph) {
      this.keytop = new SJIS(keytop);
      this.code = code;
      this.hasGlyph = hasGlyph;
    }

    public SJIS keytop() { return keytop; }
    public int code() { return code; }
    public boolean hasGlyph() { return hasGlyph; }

    private static Map<Integer,Key>codeMap;

    /** キーコードからKeyを取得する */
    public static Key findKey(int code) {
      if (codeMap == null) {
        codeMap = new HashMap<Integer,Key>();
        for (Key key: values()) {
          codeMap.put(key.code, key);
        }
      }
      return codeMap.get(code);
    }

    public static EnumSet<Key>getKeySet(Key[]keys) {
      EnumSet<Key>keySet = EnumSet.noneOf(Key.class);
      for (Key key: keys) keySet.add(key);
      return keySet;
    }

    /** コマンド中に格納される特殊キーの数 */
    public static final int SPECIAL_KEYS_IN_COMMAND = 10;
  }

  /** 変数タイプ */
  public enum Type {
    STRING, // Shift-JIS
    INTEGER, // ３２ビット整数(?)
    FLOAT; // ３２ビット浮動小数点数(?)

    /** 渡されたオブジェクトを型に合うように適当に変換する。
     * ※Keyクラスは整数と同等に扱われる。 */
    @SuppressWarnings("unchecked")
    public <T> T convert(Object o) {
      switch (this) {
      case STRING:
        if (o instanceof SJIS) return (T)o;
        if (o instanceof String) return (T)new SJIS((String)o);
        return (T)new SJIS("" + o);

      case INTEGER:
        if (o instanceof Integer) return (T)o;
        if (o instanceof Number) return (T)new Integer(((Number)o).intValue());
        if (o instanceof Key) return (T)new Integer(((Key)o).code()); // ※
        try {
          return (T)Integer.valueOf(("" + o).trim());
        } catch (Exception ex) {
        }
        return (T)new Integer(0);

      case FLOAT:
        if (o instanceof Float) return (T)o;
        if (o instanceof Number) return (T)new Float(((Number)o).floatValue());
        if (o instanceof Key) return (T)new Float(((Key)o).code()); // ※
        try {
          return (T)Float.valueOf(("" + o).trim());
        } catch (Exception ex) {
        }
        return (T)new Float(0);
      }
      assert(false);
      return null;
    }

    /** 指定オブジェクトの型はそのまま代入可能か? */
    public boolean isCompatible(Object o) {
      switch (this) {
      case STRING: return o instanceof SJIS;
      case INTEGER: return o instanceof Integer;
      case FLOAT: return o instanceof Float;
      }
      assert(false);
      return  false;
    }
  }

  /** レジスタ */
  public enum Register {
    DAT1(Type.STRING, false, 1),
    DAT2(Type.STRING, false, 2),
    DAT3(Type.STRING, false, 3),
    DAT4(Type.STRING, false, 4),
    DAT5(Type.STRING, false, 5),
    DAT6(Type.STRING, false, 6),
    DAT7(Type.STRING, false, 7),
    DAT8(Type.STRING, false, 8),
    DAT9(Type.STRING, false, 9),
    DAT10(Type.STRING, false, 10),

    INT1(Type.INTEGER, false, 1),
    INT2(Type.INTEGER, false, 2),
    INT3(Type.INTEGER, false, 3),
    INT4(Type.INTEGER, false, 4),
    INT5(Type.INTEGER, false, 5),
    INT6(Type.INTEGER, false, 6),
    INT7(Type.INTEGER, false, 7),
    INT8(Type.INTEGER, false, 8),
    INT9(Type.INTEGER, false, 9),
    INT10(Type.INTEGER, false, 10),

    FLT1(Type.FLOAT, false, 1),
    FLT2(Type.FLOAT, false, 2),
    FLT3(Type.FLOAT, false, 3),
    FLT4(Type.FLOAT, false, 4),
    FLT5(Type.FLOAT, false, 5),
    FLT6(Type.FLOAT, false, 6),
    FLT7(Type.FLOAT, false, 7),
    FLT8(Type.FLOAT, false, 8),
    FLT9(Type.FLOAT, false, 9),
    FLT10(Type.FLOAT, false, 10),

    USR1(Type.STRING, false, 1),
    USR2(Type.STRING, false, 2),
    USR3(Type.STRING, false, 3),

    DATE1(Type.STRING, true, 1),
    DATE2(Type.STRING, true, 2),
    DATE3(Type.STRING, true, 3),
    DATE4(Type.STRING, true, 4),
    DATE5(Type.STRING, true, 5),
    DATE6(Type.STRING, true, 6),
    DATE7(Type.STRING, true, 7),
    DATE8(Type.STRING, true, 8),
    DATE9(Type.STRING, true, 9),
    DATEA(Type.STRING, true, 0),
    DATEB(Type.STRING, true, 0),
    DATEC(Type.STRING, true, 0),
    DATED(Type.STRING, true, 0),
    TIME1(Type.STRING, true, 1),
    TIME2(Type.STRING, true, 2),
    TIME3(Type.STRING, true, 3),
    TIME4(Type.STRING, true, 4),

    HTID(Type.STRING, true, 0),
    BST1(Type.INTEGER, true, 1),
    BST2(Type.INTEGER, true, 2),
    BST3(Type.INTEGER, true, 3),
    BST4(Type.INTEGER, true, 4),
    RSLT(Type.INTEGER, true, 0),
    // ZERO?
    // SPACE?
    BRCOD(Type.INTEGER, true, 0),
    NUMBR(Type.INTEGER, true, 0),
    ENDKY(Type.INTEGER, true, 0);

    /** 変数タイプ */
    private Type type;

    /** システム変数 */
    private boolean system;

    /** 同じ種類の中での番号 */
    private int number;

    private Register(Type type, boolean system, int number) {
      this.type = type;
      this.system = system;
      this.number = number;
    }

    public Type type() { return type; }
    public boolean system() { return system; }
    public int number() { return number; }

    static Map<SJIS,Register>nameMap;

    /** レジスタ名称からRegisterを取得 */
    public static Register findRegister(SJIS name) {
      if (nameMap == null) {
        nameMap = new HashMap<SJIS,Register>();
        for (Register reg: Register.values()) {
          nameMap.put(new SJIS(reg.toString()), reg);
        }
      }
      return nameMap.get(name);
    }

    /** DAT1～DAT10 */
    public static final EnumSet<Register>datSet =
      EnumSet.range(Register.DAT1, Register.DAT10);

    /** INT1～INT10 */
    public static final EnumSet<Register>intSet =
      EnumSet.range(Register.INT1, Register.INT10);

    /** FLT1～FLT10 */
    public static final EnumSet<Register>fltSet =
      EnumSet.range(Register.FLT1, Register.FLT10);

    /** USR1～USR3 */
    public static final EnumSet<Register>usrSet =
      EnumSet.range(Register.USR1, Register.USR3);

    /** DATE1～DATED */
    public static final EnumSet<Register>dateSet =
      EnumSet.range(Register.DATE1, Register.DATED);

    /** TIME1～TIME4 */
    public static final EnumSet<Register>timeSet =
      EnumSet.range(Register.TIME1, Register.TIME4);

    /** DATE?, TIME? */
    public static final EnumSet<Register>dateTimeSet;
    static {
      dateTimeSet = EnumSet.noneOf(Register.class);
      dateTimeSet.addAll(dateSet);
      dateTimeSet.addAll(timeSet);
    }

    /** BST1～BST4 */
    public static final EnumSet<Register>bstSet =
      EnumSet.range(Register.BST1, Register.BST4);

    /** 代入可能 */
    public static final EnumSet<Register>assignableSet;
    static
    {
      assignableSet = EnumSet.noneOf(Register.class);
      assignableSet.addAll(datSet);
      assignableSet.addAll(intSet);
      assignableSet.addAll(fltSet);
    }

    /** 文字列 */
    public static final EnumSet<Register>stringSet;
    static
    {
      stringSet = EnumSet.noneOf(Register.class);
      stringSet.addAll(datSet);
      stringSet.addAll(dateSet);
      stringSet.addAll(timeSet);
      stringSet.add(Register.HTID);
      stringSet.addAll(usrSet);
    }
  }


  /** 比較オペレータ */
  public enum Comp {
    EQ("=", "<>"),
    GT(">", "<="),
    LT("<", ">="),
    GE(">=", "<"),
    LE("<=", ">"),
    NE("<>", "=");

    /** オペレータ名称 */
    private SJIS string;

    /** 逆オペレータ名称 */
    private SJIS oppoStr;

    /** 逆オペレータ */
    private Comp opposite;

    /** 作成する */
    private Comp(String string, String oppoStr) {
      this.string = new SJIS(string);
      this.oppoStr = new SJIS(oppoStr);
    }

    public SJIS string() { return string; }

    /** 逆オペレータを取得 */
    public Comp opposite() {
      if (opposite == null) {
        opposite = findComp(oppoStr);
      }
      return opposite;
    }

    private static Map<SJIS,Comp>opeMap;

    /** オペレータ名称からオペレータを得る */
    public static Comp findComp(SJIS ope) {
      if (opeMap == null) {
        opeMap = new HashMap<SJIS,Comp>();
        for (Comp comp: Comp.values()) {
          opeMap.put(comp.string, comp);
        }
      }
      return opeMap.get(ope);
    }
  }

  /** ファイル位置付け */
  public enum FilePos {
    NEXT,
    TOP,
    PREV,
    CUR,
    BOT;

    private static Map<SJIS,FilePos>nameMap;

    public static FilePos findPos(SJIS name) {
      if (nameMap == null) {
        nameMap = new HashMap<SJIS,FilePos>();
        for (FilePos pos: values()) {
          nameMap.put(new SJIS(pos.toString()), pos);
        }
      }
      return nameMap.get(name);
    }
  }

  /** フル桁入力時アクション */
  public enum FullAction {
    IMMEDIATE, // すぐ
    ONESECOND, // １秒後
    NOTHING; // なし（ENTキー必要)


    public static FullAction find(int value) {
      FullAction[]values = values();
      if (value < 0 || value >= values.length) return null;
      return values[value];
    }
  }


  /** 文字列を格納するものだが、実際の文字列SJISか、あるいは文字列を格納する
   * DATレジスタかを選択できる */
  public static class SJIS_DAT {

    /** ファイル名称の場合 not null */
    protected SJIS sjis;

    /** レジスタの場合 not null */
    protected Register datReg;

    public SJIS_DAT(String s) {
      this(new SJIS(s));
    }

    public SJIS_DAT(SJIS sjis) {
      this.sjis = sjis;
    }

    public SJIS_DAT(Register datReg) {
      this.datReg = datReg;
      Register.datSet.contains(datReg);
    }

    /** ファイル名称そのものを取り出す */
    public SJIS getSJIS() {
      return sjis;
    }

    /** レジスタを取り出す */
    public Register getRegister() {
      return datReg;
    }

    public String toString() {
      if (sjis != null) return sjis.toString();
      return datReg.toString();
    }
  }

  /** ファイル名称 */
  public static class Filename extends SJIS_DAT {
    public Filename(String s) {
      this(new SJIS(s));
    }
    public Filename(SJIS sjis) {
      super(sjis);
      assert(sjis.length() <= 12);
    }
    public Filename(Register r) {
      super(r);
    }
  }

  /** マシン種類 */
  public enum Machine {
    PHL1600("PHL-1600", 1600),
    PHL2600("PHL-2600", 2600);
    private String label;
    private int number;
    private Machine(String label, int number) {
      this.label = label;
      this.number = number;
    }
    public String label() { return label; }
    public int number() { return number; }
    public static Machine numberToMachine(int number) {
      for (Machine machine: values()) {
        if (machine.number == number) return machine;
      }
      return null;
    }
    public static String[]allLabels() {
      Machine[]values = values();
      String[]labels = new String[values.length];
      for (int i = 0; i < values.length; i++)
        labels[i] = values[i].label;
      return labels;
    }
    public String toString() {
      return label;
    }
  }

  /** フォントサイズ */
  public enum FontSize {
    SIZE12(12),
    SIZE16(16);
    private int size;
    private FontSize(int size) {
      this.size = size;
    }
    public int size() { return size; }
    public static FontSize findFontSize(int size) {
      for (FontSize f: values())
        if (f.size == size) return f;
      return null;
    }
    public String toString() {
      return "" + size;
    }
  }

  /** プロファイル */
  public enum Profile {

    PHL1600_12(Machine.PHL1600, FontSize.SIZE12,  8, 16),
    PHL1600_16(Machine.PHL1600, FontSize.SIZE16,  6, 12),
    PHL2600_12(Machine.PHL2600, FontSize.SIZE12, 10, 20),
    PHL2600_16(Machine.PHL2600, FontSize.SIZE16,  8, 16);

    private Machine machine;
    private FontSize fontSize;
    private int rows;
    private int columns;

    private Profile(Machine machine, FontSize fontSize, int rows, int columns) {
      this.machine = machine;
      this.fontSize = fontSize;
      this.rows = rows;
      this.columns = columns;
    }

    public Machine machine() { return machine; }
    public FontSize fontSize() { return fontSize; }
    public int rows() { return rows; }
    public int columns() { return columns; }

    /** マシンとフォントサイズからプロファイルを得る */
    public static Profile findProfile(Machine machine, FontSize fontSize) {
      for (Profile prof: values()) {
        if (prof.machine == machine && prof.fontSize == fontSize)
          return prof;
      }
      return null;
    }

    public String toString() {
      return "" + machine + "," + fontSize + "," + columns + "," + rows;
    }
  }

  /** スタート画面 */
  public enum StartScreen {
    TWOSECONDS(0), // 表示し、二秒後にメイン画面
    ANYKEY(1), // 表示し、ANYKEYでメイン画面
    NONE(2); // 表示しない
    private int number;
    private StartScreen(int number) {
      this.number = number;
    }
    public int number() { return number; }
    public static StartScreen findStartScreen(int number) {
      for (StartScreen ss: values())
        if (ss.number == number) return ss;
      return null;
    }
  }

  /** カーソル形状 */
  public enum CursorShape {
    NONE(0), // カーソルなし
    UNDERBAR(1), // アンダーバー
    BLOCK(2); // ブロック
    private int number;
    private CursorShape(int number) {
      this.number = number;
    }
    public int number() { return number; }
    public static CursorShape findCursorShape(int number) {
      for (CursorShape cs: values())
        if (cs.number == number) return cs;
      return null;
    }
  }

  /** 表示属性 */
  public static final byte DISPATTR_QUARTER = 1;
  public static final byte DISPATTR_REVERSE = 2;
}
