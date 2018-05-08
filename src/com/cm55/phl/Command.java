// Created by Cryptomedia Co., Ltd. 2006/06/07
package com.cm55.phl;

import java.util.*;
import java.util.regex.*;

import com.cm55.phl.PHL.*;
import com.cm55.phl.gen.*;

/**
 * コマンド
 * <p>
 * PHLシリーズのコマンドをオブジェクト化したもの。
 * </p>
 */

public abstract class Command {

  public Cmd cmd;

  protected Command(Cmd cmd) {
    this.cmd = cmd;
  }

  public abstract void write(HTCWriter writer);


  /////////////////////////////////////////////////////////////////////////////

  /** タイトル */
  
  public static final class Title extends Command {

    /** タイトル最大バイト数 */
    public static final int TITLE_LEN = 20;

    /** バージョン最大バイト数 */
    public static final int VERSION_LEN = 5;

    /** タイトル */
    public SJIS title;

    /** バージョン */
    public SJIS version;

    /** 起動画面の表示 */
    public StartScreen startScreen = StartScreen.TWOSECONDS;

    /** キークリック音 */
    public boolean clickSound = true;

    /** プロファイル */
    public Profile profile = Profile.PHL1600_12;

    /** プライベート */
    private Title() {
      super(Cmd.Title);
    }

    /** 読込 */
    public Title(HTCReader reader) {
      this();
      read(reader);
    }

    /** タイトル、バージョンを指定して作成 */
    public Title(String title, String version) {
      this(new SJIS(title), new SJIS(version));
    }

    /** タイトル、バージョンを指定して作成 */
    public Title(SJIS title, SJIS version) {
      this();
      this.title = title;
      this.version = version;
      if (title.length() > TITLE_LEN || version.length() >  VERSION_LEN)
        throw new GenerateException("タイトルあるいはバージョン文字列が長すぎます");
    }

    /** 開始画面を設定 */
    public Title setStartScreen(StartScreen ss) {
      this.startScreen = ss;
      return this;
    }

    /** プロファイルを設定 */
    public Title setProfile(Profile profile) {
      this.profile = profile;
      return this;
    }

    /** キークリック音を設定 */
    public Title setClickSound(boolean value) {
      clickSound = value;
      return this;
    }

    /** 読込 */
    protected void read(HTCReader reader) {
      title = reader.getBytes(20).trim();
      reader.skip(1);
      version = reader.getBytes(5).trim();
      startScreen = StartScreen.findStartScreen(reader.getInt(1));
      if (startScreen == null)
        reader.readException("開始画面種類が不明です");
      FontSize fontSize = FontSize.findFontSize(reader.getInt(2));
      if (fontSize == null)
        reader.readException("フォントサイズが不明です");
      clickSound = reader.getInt(1) == 1;
      Machine machine = Machine.numberToMachine(reader.getInt(4));
      if (machine == null) reader.readException("機種が不明です");
      profile = Profile.findProfile(machine, fontSize);
      if (profile == null) reader.readException("機種・フォントサイズが解釈できません");
    }

    /** 書込み */
    @Override
    public void write(HTCWriter writer) {
      writer.putSJIS(title.forceSize(20, (byte)0x20));
      writer.padding((byte)0x20, 1);
      writer.putSJIS(version.forceSize(5, (byte)0x20));
      writer.putInt(startScreen.number(), 1);
      writer.putInt(profile.fontSize().size(), 2);
      writer.putInt(clickSound? 1:0, 1);
      writer.putInt(profile.machine().number(), 4);
    }
  }

  /** 情報 */
  
  public static final class BarcodeInfo extends Command {
    public BarcodeInfoSub sub;
    private BarcodeInfo() {
      super(Cmd.BarcodeInfo);
    }

    public BarcodeInfo(HTCReader reader) {
      this();
      read(reader);
    }

    public BarcodeInfo(int number) {
      this();
      switch (number) {
      case 1: sub = new BarcodeInfoSub1(); break;
      case 2: sub = new BarcodeInfoSub2(); break;
      default:
        throw new WriteException("バーコード情報番号が違います");
      }
    }
    public BarcodeInfo(BarcodeInfoSub sub) {
      this();
      this.sub = sub;
    }

    protected void read(HTCReader reader) {
      int number = reader.getInt(1);
      switch (number) {
      case 1: sub = new BarcodeInfoSub1(); break;
      case 2: sub = new BarcodeInfoSub2(); break;
      default:
        reader.readException("バーコード情報番号がわかりません");
      }
      sub.read(reader);
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putInt(sub.number, 1);
      sub.write(writer);
    }

    public String toString() {
      return "" + cmd + " " + sub;
    }
  }

  public abstract static class BarcodeInfoSub {
    int number;
    protected BarcodeInfoSub(int number) {
      this.number = number;
    }
    public abstract void read(HTCReader reader);
    public abstract void write(HTCWriter writer);
  }

  // 00***000060204800312
  public static class BarcodeInfoSub1 extends BarcodeInfoSub {

    public int barcodeMap = 0x95;

    public BarcodeInfoSub1() { super(1); }
    public void read(HTCReader reader) {
      reader.skip(2);
      barcodeMap = reader.getInt(3);
      reader.skip(15); // バーコード詳細設定
    }

    public void write(HTCWriter writer) {
      writer.putInt(0, 2);
      writer.putInt(barcodeMap, 3);
      writer.putSJIS(new SJIS("000060204800312")); // バーコード詳細設定
    }
  }

  public static class BarcodeInfoSub2 extends BarcodeInfoSub {

    public int[]yomitoriketa = new int[5];
    public int buzzarFreq = 1950; // 接続時ブザー周波数
    public int buzzarMs = 5; // ブザー長さ
    public int lazar = 5;
    public int shougou = 1; // 照合回数
    public int vib = 20;

    public BarcodeInfoSub2() { super(2); }
    public void read(HTCReader reader) {

      for (int i = 0; i < 5; i ++) {
        yomitoriketa[i] = reader.getInt(3);
      }
      reader.skip(1);
      buzzarFreq = reader.getInt(4);
      buzzarMs = reader.getInt(4);
      reader.skip(2);
      lazar = reader.getInt(2);
      reader.skip(1);
      shougou = reader.getInt(1);
      vib = reader.getInt(4);
    }
    public void write(HTCWriter writer) {
      for (int i = 0; i < 5; i ++) {
        writer.putInt(yomitoriketa[i], 3);
      }
      writer.putInt(0, 1);
      writer.putInt(buzzarFreq, 4);
      writer.putInt(buzzarMs, 4);
      writer.putInt(0, 2);
      writer.putInt(lazar, 2);
      writer.putInt(0, 1);
      writer.putInt(shougou , 1);
      writer.putInt(vib, 4);
    }
  }

  /////////////////////////////////////////////////////////////////////////////

  /** ラベル */
  
  public static final class Label extends Command {

    static Pattern pattern = Pattern.compile("(\\d+),(\\d+)");

    /** ラベル番号 */
    private int number;

    /** ボディオフセット位置 */
    private int offset;

    /** コマンドポインタ */
    private int index;

    public Label() {
      super(Cmd.Label);
    }

    private Label(HTCReader reader) {
      this();
      read(reader);
    }

    /** これが呼ばれるのはHTCコード生成時である（読込時は呼ばれない）。
     * コード生成時に、コマンド列内の複数位置にラベルが登録されているとまずいので、
     * このメソッドが一度しか呼ばれないことを保証する。
     */
    public void setNumber(int number) {
      if (this.number != 0 || number == 0)
        throw new WriteException("プログラムエラー：ラベル番号設定処理が異常です");

      this.number = number;
    }

    /** ラベル番号を取得 */
    public int getNumber() {
      assert(number != 0);
      return number;
    }

    /** コード生成時に一度だけ呼び出される */
    public void setOffset(int offset) {
      if (this.offset != 0)
        throw new WriteException("プログラムエラー：ラベルオフセット設定が異常");
      this.offset = offset;
    }

    public int getOffset() {
      return offset;
    }

    /** これは読込時に一度だけ行われる。コマンド列の何番目にあるかを示す。
     * 実行時、Jump側はLabelへの参照を保持しているが、コマンド列のインデックス
     * は保持していない。
     * もしコマンド列に二重に登録されていた場合は二重に呼び出される。
     */
    public void setIndex(int index) {
      assert(this.index == 0);
      this.index = index;
    }

    /** コマンド列インデックスを返す */
    public int getIndex() {
      return index;
    }

    /** パラメータ読込 */
    protected void read(HTCReader reader) {
      String s = reader.getAllBytes().toString();
      Matcher matcher = pattern.matcher(s);
      matcher.matches();
      number = Integer.parseInt(matcher.group(1));
      offset = Integer.parseInt(matcher.group(2));
    }

    /** パラメータ書込み */
    @Override
    public void write(HTCWriter writer) {
      try (Formatter f = new Formatter()) {
        f.format("%d,%d", number, offset);
        writer.putString(f.toString());
      }
    }

    public String toString() {
      return "" + cmd  + "_" + number;
    }
  }

  /** 表示クリア */
  
  public static class DisplayClear extends Command {

    public DisplayClear() {
      super(Cmd.DisplayClear);
    }

    public DisplayClear(HTCReader reader) {
      this();
    }

    @Override
    public void write(HTCWriter writer) {
    }

    public String toString() {
      return "" + cmd;
    }
  }

  /** 表示 */
  public abstract static class Display extends Command {

    /** x座標 */
    public int x;

    /** y座標 */
    public int y;

    /** クリアバイト数 */
    public int clearBytes;

    protected Display(Cmd cmd) {
      super(cmd);
    }
  }

  /** 表示 */
  
  public static final class DisplayString extends Display {

    /** 文字列サイズ */
    public int size;

    /** 表示文字列 */
    public SJIS sjis;

    /** 作成する */
    private DisplayString() {
      super(Cmd.DisplayString);
    }

    public DisplayString(HTCReader reader) {
      this();
      read(reader);
    }

    public DisplayString(int y, int x, String string) {
      this(y, x, new SJIS(string));
    }

    public DisplayString(int y, int x, SJIS string) {
      this();
      this.x = x;
      this.y = y;
      this.sjis = string;
      this.size = sjis.length();
    }

    public DisplayString setClear(int clearBytes) {
      this.clearBytes = clearBytes;
      return this;
    }

    /** 読込 */
    protected  void read(HTCReader reader) {
      x = reader.getInt(2);
      y = reader.getInt(2);
      reader.skip(2);
      clearBytes = reader.getInt(2);
      size = reader.getInt(2);
      sjis = reader.getAllBytes();
      if (size != sjis.length())
        reader.readException("表示文字列サイズが指定サイズと違います");
    }

    /** 書き込み */
    @Override
    public void write(HTCWriter writer) {
      writer.putInt(x, 2);
      writer.putInt(y, 2);
      writer.padding((byte)'0', 2);
      writer.putInt(clearBytes, 2);
      writer.putInt(size, 2);
      writer.putSJIS(sjis);
    }

    /** 文字列化 */
    public String toString() {
      return "" + cmd + " x:" + x + ", y:" + y + ", clearBytes:" + clearBytes +
        ", size:" + size + ", sjis:" + sjis;
    }
  }

  /** 変数表示 */
  
  public static final class DisplayRegister extends Display {

    /** 1/4角 */
    protected boolean quarter = false;

    /** 反転 */
    protected boolean reverse = false;

    /** スタート位置 */
    protected int start;

    /** 長さ */
    protected int length;

    /** 変数 */
    protected Register register;

    public boolean getQuarter() { return quarter; }
    public boolean getReverse() { return reverse; }
    public int getStart() { return start; }
    public int getLength() { return length; }
    public Register getRegister() { return register; }

    private DisplayRegister() {
      super(Cmd.DisplayRegister);
    }

    public DisplayRegister(HTCReader reader) {
      this();
      read(reader);
    }

    public DisplayRegister(int y, int x, Register register, int start, int length) {
      this();
      this.x = x;
      this.y = y;
      this.register = register;
      this.start = start;
      this.length = length;
    }

    protected void read(HTCReader reader) {
      x = reader.getInt(2);
      y = reader.getInt(2);
      quarter = reader.getInt(1) == 1;
      reverse = reader.getInt(1) == 1;
      reader.skip(2);
      start = reader.getInt(2) - 1; // 0ベースに
      length = reader.getInt(2);
      register = reader.getRegister();
      clearBytes = reader.getInt(2);
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putInt(x, 2);
      writer.putInt(y, 2);
      writer.putInt(quarter? 1:0, 1);
      writer.putInt(reverse? 1:0, 1);
      writer.padding((byte)'0', 2); // ???
      writer.putInt(start + 1, 2); // 1ベースに
      writer.putInt(length, 2);
      writer.putRegister(register);
      writer.putInt(clearBytes, 2);
    }

    public String toString() {
      return "DisplayRegister " + x + "," + y + "," + quarter + "," + reverse +
        "," + start + "," + length + "," + register + "," + clearBytes + ",";
    }
  }

  /** エコー無し入力 */
  
  public static final class NoEchoInput extends Command {

    public Register register;

    private NoEchoInput() {
      super(Cmd.NoEchoInput);
    }

    public NoEchoInput(HTCReader reader) {
      this();
      read(reader);
    }

    public NoEchoInput(Register register) {
      this();
      this.register = register;
    }

    protected void read(HTCReader reader) {
      register = reader.getRegister();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putRegister(register);
    }

    public String toString() {
      return "NoEchoInput " + register;
    }
  }

  /** 行入力 */
  public abstract static class LineInput extends Command {

    /** x位置 */
    public int x;

    /** y位置 */
    public int y;

    /** 入力先変数 */
    public Register register;

    /** 入力サイズ */
    public int columns;

    /** 特殊キー */
    public EnumSet<Key>specialKeys;

    /** フル桁入力時
     * ０：すぐ、１：１秒後、２：ＥＮＴ
     */
    public FullAction fullAction;

    protected LineInput(Cmd cmd) {
      super(cmd);
    }



    /** 座標だけを読込 */
    protected void read(HTCReader reader) {
      x = reader.getInt(2);
      y = reader.getInt(2);
    }
  }

  /** エコー付き入力 */
  
  public static final class EchoedInput extends LineInput {

    /** エコーなし */
    public boolean noEcho = false;

    /** カーソル表示
     * ０：なし、１：アンダーバー、２：ブロック
     */
    public CursorShape cursor = CursorShape.UNDERBAR;
    public boolean minus = false;

    public String toString() {
      return
        "EchoedInput x:" + x + ", y:" + y + ", register:" + register +
        ", columns:" + columns + ", noEcho:" + noEcho + ", full:" + fullAction +
        ",specialKeys:" + specialKeys + ", cursor:" + cursor + ", minus:" + minus;
    }

    /** 作成 */
    private EchoedInput() {
      super(Cmd.EchoedInput);

      fullAction = FullAction.IMMEDIATE;
    }

    /** フル桁入力時アクションを設定する */
    public EchoedInput setFullAction(FullAction act) {
      fullAction = act;
      return this;
    }

    public EchoedInput(HTCReader reader) {
      this();
      read(reader);
    }

    public EchoedInput(int y, int x, Register register, int columns,
        EnumSet<Key>specialKeys) {
      this();
      this.x = x;
      this.y = y;
      this.register = register;
      this.columns = columns;
      this.specialKeys = specialKeys;
      assert(specialKeys.size() <= Key.SPECIAL_KEYS_IN_COMMAND);
    }

    public EchoedInput setNoEcho(boolean value) {
      noEcho = value;
      return this;
    }

    /** 読込 */
    @Override
    protected void read(HTCReader reader) {
      super.read(reader);
      register = reader.getRegister();
      columns = reader.getInt(3);
      noEcho = reader.getInt(1) == 1;
      fullAction = reader.getFullAction();
      specialKeys = reader.getKeySet();
      cursor = CursorShape.findCursorShape(reader.getInt(1));
      if (cursor == null)
        reader.readException("カーソル形状が不明です");
      minus = reader.getInt(1) == 1;
    }

    /** 書き込み */
    @Override
    public void write(HTCWriter writer) {
      writer.putInt(x, 2);
      writer.putInt(y, 2);

      writer.putRegister(register);
      writer.putInt(columns, 3);
      writer.putInt(noEcho?1:0, 1);
      writer.putFullAction(fullAction);
      writer.putKeySet(specialKeys);
      writer.putInt(cursor.number(), 1);
      writer.putInt(minus?1:0, 1);
    }
  }

  /** バーコード入力 */
  
  public static final class BarcodeInput extends LineInput {

    // 065I020001010130000000000000110241                           21
    // 065I020001010130000000000000111241                           20
    // 065I020001010130000000000000101241                           00
    // 065I020001010130000000000010001241                           00
    // 065I020001010130000000000011001241                           00

    /** 正読時バイブレーション */
    public boolean viblation = false;

    /** 新規・継続スキャン指定 */
    public boolean continuedScan = false;

    /** 正読時ブザー・LED */
    public boolean buzzarLed = true;

    /** 読み取り終了条件指定２ */
    public boolean keyInterruption = true;

    /* 読み取り終了条件指定１ */
    public boolean ignoreTriggerButton = false;

    /** アンダーバーカーソル */
    public boolean underbarCursor = true;

    private BarcodeInput() {
      super(Cmd.BarcodeInput);

      fullAction = FullAction.NOTHING;
    }

    public BarcodeInput(HTCReader reader) {
      this();
      read(reader);
    }

    public BarcodeInput(int y, int x, Register register, int columns,
        EnumSet<Key>specialKeys) {
      this();
      this.x = x;
      this.y = y;
      this.register = register;
      this.columns = columns;
      this.specialKeys = specialKeys;
    }

    /** フル桁入力時アクションを設定する */
    public BarcodeInput setFullAction(FullAction act) {
      fullAction = act;
      return this;
    }

    /** 読込 */
    @Override
    protected void read(HTCReader reader) {
      super.read(reader);
      register = Register.findRegister(new SJIS("DAT" + reader.getInt(2)));
      columns = reader.getInt(3);
      reader.skip(11);
      viblation = reader.getInt(1) == 1; // 正読時バイブレーション
      continuedScan = reader.getInt(1) == 1; // 新規・継続スキャン指定
      buzzarLed = reader.getInt(1) == 1; // 正読時読み取り指定
      keyInterruption = reader.getInt(1) == 1; // 読み取り終了条件指定２
      ignoreTriggerButton = reader.getInt(1) == 1; // 読み取り終了条件指定１
      specialKeys = reader.getKeySet(); // 特殊キー
      fullAction = reader.getFullAction(); // 次項目移行
      underbarCursor = reader.getInt(1) == 1; // カーソル表示
    }

    /** 書き込み */
    @Override
    public void write(HTCWriter writer) {
      writer.putInt(x, 2);
      writer.putInt(y, 2);
      writer.putInt(register.number(), 2);
      writer.putInt(columns, 3);
      writer.putInt(0, 11);
      writer.putInt(viblation? 1:0, 1);
      writer.putInt(continuedScan? 1:0, 1);
      writer.putInt(buzzarLed? 1:0, 1);
      writer.putInt(keyInterruption? 1:0, 1);
      writer.putInt(ignoreTriggerButton? 1:0, 1);
      writer.putKeySet(specialKeys);
      writer.putFullAction(fullAction);
      writer.putInt(underbarCursor? 1:0, 1);
    }

    public String toString() {
      return "BarcodeInput x:" + x + ", y:" + y + ", register:" + register +
        ", columns:" + columns + ", specialKeys:" + specialKeys +", viblation:" + viblation +
        ", continuedScan:" + continuedScan + ", buzzarLed:" + buzzarLed +
        ", keyInterruption:" + keyInterruption + ", ignoreTriggerButton:" + ignoreTriggerButton +
        ", fullAction:" + fullAction + ", underbarCursor:" + underbarCursor;
    }
  }

  /** 無条件ジャンプ。
   * 読込時はまずラベル番号だけを取得し、対象ラベルは後から設定する。
   * 作成時は、対象ラベルを設定し、ラベル番号を後から決定し、その後で書込みが行われる。
   */
  public abstract static class AbstractJump extends Command {

    /** ラベル番号 */
    protected int labelNumber;

    /** 対象ラベル */
    protected Label targetLabel;

    protected AbstractJump(Cmd cmd) {
      super(cmd);
    }

    public AbstractJump(Cmd cmd, int number) {
      this(cmd);
      this.labelNumber = number;
    }

    public AbstractJump(Cmd cmd, Label label) {
      this(cmd);
      targetLabel = label;
    }

    public void setLabelNumber(int number) {
      assert(labelNumber == 0);
      labelNumber = number;
    }
    public void setTargetLabel(Label label) {
      assert(targetLabel == null);
      targetLabel = label;
    }

    public void replaceTargetLabel(Label label) {
      assert(targetLabel != null && label != null && targetLabel != label);
      targetLabel = label;
    }
    public Label getTargetLabel() {
      assert(targetLabel != null);
      return targetLabel;
    }

    protected void read(HTCReader reader) {
      labelNumber = reader.getInt(4);
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putInt(labelNumber, 4);
    }
  }

  
  public static final class Jump extends AbstractJump {
    private Jump() {
      super(Cmd.Jump);
    }
    public Jump(HTCReader reader) {
      this();
      read(reader);
    }

    public Jump(int number) { super(Cmd.Jump, number); }
    public Jump(Label label) { super(Cmd.Jump, label); }

    public String toString() {
      return "Jump " + targetLabel;
    }
  }

  /** 条件ジャンプ */
  
  public static final class JumpIf extends AbstractJump {

    public Comp comp;
    public Register register;
    public Object value;

    /** 作成 */
    public JumpIf() {
      super(Cmd.JumpIf);
    }

    public JumpIf(HTCReader reader) {
      this();
      read(reader);
    }

    public JumpIf(Register register, Comp comp, Object value, int number) {
      super(Cmd.JumpIf, number);
      this.comp = comp;
      this.register = register;
      this.value = register.type().convert(value);
      this.labelNumber = number;
      assert(number != 0);
    }

    public JumpIf(Register register, Comp comp, Object value, Label label) {
      super(Cmd.JumpIf, label);
      this.comp = comp;
      this.register = register;
      this.value = register.type().convert(value);
      assert(label != null);
      this.targetLabel = label;
    }

    @Override
    protected void read(HTCReader reader) {

      //labelNumber = reader.getInt(4);
      super.read(reader);
      comp = reader.getComp();
      register = reader.getRegister();
      SJIS sjis = reader.getAllBytes();
      assert(register != null && comp != null);

      // ここの仕様はかなりいい加減。比較対象がレジスター名称であれば
      // そのレジスターの内容と比較するが、そうでなければ値として比較するように
      // なっているらしい。

      value = Register.findRegister(sjis);
      if (value != null) {
        if (register.type() != ((Register)value).type())
          reader.parseException();
        return;
      }
      value = register.type().convert(sjis);
    }

    @Override
    public void write(HTCWriter writer) {
      super.write(writer);
      //writer.putInt(labelNumber, 4);
      writer.putComp(comp);
      writer.putRegister(register);
      if (value instanceof Register) writer.putRegister((Register)value);
      else                  writer.putString("" + value);
    }

    /** 文字列化 */
    public String toString() {
      Key key = null;
      try {
        int code = Integer.parseInt("" + value);
        key = Key.findKey(code);
      } catch (Exception ex) {
      }

      StringBuilder s = new StringBuilder();

      s.append("JumpIf left=" + register + " " + comp.string() +
        " value=" + value);
      if (key != null) s.append("(" + key.keytop() + ")");
      s.append(", label= " + targetLabel);

      return s.toString();

    }
  }

  /** マスタ検索 */
  
  public static final class MasterSearch extends Command {

    public static final int METHOD_LINEAR = 0;
    public static final int METHOD_BINARY = 1;

    public Filename filename;
    public int recordLen;
    public Register keyReg1;
    public int keyPos1;
    public int keySize1;
    public Register keyReg2;
    public int keyPos2;
    public int keySize2;
    public int method = 2; // 0: 逐次、2:二分

    /** TOP:先頭のレコードから検索、
     * NEXT:次のレコードから検索、
     * PREV:現在位置の前のレコードを検索
     * CUR:現在位置から検索
     */
    public FilePos filePos = FilePos.TOP;
    public Register resultReg;

    private MasterSearch() {
      super(Cmd.MasterSearch);
    }

    public MasterSearch(HTCReader reader) {
      this();
      read(reader);
    }

    public MasterSearch(Filename filename, int recordLen,
        Register keyReg1, int keyPos1, int keySize1, Register resultReg) {
      this();
      this.filename = filename;
      this.recordLen = recordLen;
      this.keyReg1 = keyReg1;
      this.keyPos1 = keyPos1;
      this.keySize1 = keySize1;
      this.resultReg = resultReg;
    }

    protected void read(HTCReader reader) {
      filename = reader.getFilename();
      reader.skip(1); // ???
      recordLen = reader.getInt(3);
      keyReg1 = reader.getRegister();
      keyPos1 = reader.getInt(3) - 1; // 0ベースにする
      keySize1 = reader.getInt(3);
      keyReg2 = reader.getRegister(false);
      if (keyReg2 != null) {
        keyPos2 = reader.getInt(3) - 1; // 0ベースにする
        keySize2 = reader.getInt(3);
      } else {
        reader.skip(6);
      }
      method = reader.getInt(1);
      filePos = reader.getFilePos();
      resultReg = reader.getRegister();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putFilename(filename);
      writer.padding((byte)'0', 1);
      writer.putInt(recordLen, 3);
      writer.putRegister(keyReg1);
      writer.putInt(keyPos1 + 1, 3); // 1ベースにする
      writer.putInt(keySize1, 3);
      if (keyReg2 == null) {
        writer.padding((byte)' ', 11);
      } else {
        writer.putRegister(keyReg2);
        writer.putInt(keyPos2 + 1, 3); // 1ベースにする
        writer.putInt(keySize2, 3);
      }
      writer.putInt(method, 1);
      writer.putFilePos(filePos);
      writer.putRegister(resultReg);
    }

    public String toString() {
      return "MasterSearch " + filename + "," + recordLen + "," + keyReg1 +
        "," + keyPos1 + "," + keySize1 + "," + keyReg2 + "," + keyPos2 + "," + keySize2 +
        "," + filePos + "," + resultReg;
    }
  }

  /** ファイル書き込み */
  
  public static final class RecordWrite extends Command {

    /** ファイル名 */
    public Filename filename;

    /** レコード長さ */
    public int recordLen;

    /** CRLFの付加 */
    public boolean crlf = false;

    /** 書き込みデータを持つレジスター */
    public Register register;

    /** 上書き。false時は追加 */
    public boolean overwrite = false;

    /** 作成する */
    private RecordWrite() {
      super(Cmd.RecordWrite);
    }

    public RecordWrite(HTCReader reader) {
      this();
      read(reader);
    }

    public RecordWrite(Filename filename, int recordLen, Register register) {
      this(filename, recordLen, register, false, false);
    }

    public RecordWrite(Filename filename, int recordLen, Register register, boolean overwrite, boolean crlf) {
      this();
      this.filename = filename;
      this.recordLen = recordLen;
      this.register = register;
      this.overwrite = overwrite;
      this.crlf = crlf;
    }

    protected void read(HTCReader reader) {
      filename = reader.getFilename();
      reader.skip(1); // ???
      recordLen = reader.getInt(3);
      crlf = reader.getInt(1) == 1;
      register = reader.getRegister();
      overwrite = reader.getInt(1) == 1;
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putFilename(filename);
      writer.putInt(0, 1); // ???
      writer.putInt(recordLen, 3);
      writer.putInt(crlf? 1:0, 1);
      writer.putRegister(register);
      writer.putInt(overwrite? 1:0, 1);
    }

    public String toString() {
      return "FileWrite " + filename + "," + recordLen + "," + crlf + "," + register + "," + overwrite;
    }
  }

  /** レコード読み取り。
   * <p>
   * レコードポインタは最初は-1を指している。このとき、TOP,BOT,PREV,NEXTのどれを
   * 行ってもエラーになる(RSLT<>0)。
   * 最初のレコードを書くと、ファイルポインタは０になる。このとき、PREV,NEXT
   * を行ってもエラーになるが、TOP,BOTは有効。
   * </p>
   * <p>
   * レコードポインタがどこにいても、追加書き込みを行うと最後に追加され、
   * レコードポインタはその最後のレコードを指す。
   * </p>
   *
   */
  
  public static final class RecordRead extends Command {

    public Filename filename;
    public int recordLen;
    public Register register;
    public FilePos filePos;

    private RecordRead() {
      super(Cmd.RecordRead);
    }

    public RecordRead(HTCReader reader) {
      this();
      read(reader);
    }

    public RecordRead(Filename filename, int recordLen, Register register, FilePos filePos) {
      this();
      this.filename = filename;
      this.recordLen = recordLen;
      this.register = register;
      this.filePos = filePos;
    }

    protected void read(HTCReader reader) {
      filename = reader.getFilename();
      reader.skip(1);
      recordLen = reader.getInt(3);
      register = reader.getRegister();
      filePos = reader.getFilePos();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putFilename(filename);
      writer.putInt(0, 1); // ???
      writer.putInt(recordLen, 3);
      writer.putRegister(register);
      writer.putFilePos(filePos);
    }

    public String toString() {
      return "" + cmd + " filename=" + filename + ", recordLen=" + recordLen +
        ", register=" + register + ", " + filePos;
    }
  }

  /** レコード数取得、RSLT=0 正常、RSLT=-1 ファイルなし
   * 取得されたレコード数は指定レジスタに格納される
   */
  
  public static final class RecordCount extends Command {
    public Filename filename;
    public int recordLen;
    public Register intReg;

    private RecordCount() {
      super(Cmd.RecordCount);
    }

    public RecordCount(HTCReader reader) {
      this();
      read(reader);
    }

    public RecordCount(Filename filename, int recordLen, Register intReg) {
      this();
      this.filename = filename;
      this.recordLen = recordLen;
      this.intReg = intReg;
    }

    protected void read(HTCReader reader) {
      filename = reader.getFilename();
      reader.skip(1); // ???
      recordLen = reader.getInt(3);
      intReg = reader.getRegister();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putFilename(filename);
      writer.putInt(0, 1); // ???
      writer.putInt(recordLen, 3);
      writer.putRegister(intReg);
    }
  }

  /** ファイルの存在を確かめる。存在しないときはRSLT=-1 */
  
  public static final class FileExists extends Command {

    public Filename filename;

    private FileExists() {
      super(Cmd.FileExists);
    }

    public FileExists(HTCReader reader) {
      this();
      read(reader);
    }

    /** ファイル名称 */
    public FileExists(Filename filename) {
      this();
      this.filename = filename;
    }

    protected void read(HTCReader reader) {
      filename = reader.getFilename();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putFilename(filename);
    }

    public String toString() {
      return "FileExists " + filename;
    }
  }

  /** 変数初期化 */
  
  public static final class VariableInit extends Command {
    public Register register;

   private VariableInit() {
      super(Cmd.VariableInit);
    }

    public VariableInit(HTCReader reader) {
      this();
      read(reader);
    }

    public VariableInit(Register register) {
      this();
      this.register = register;
    }

    public void read(HTCReader reader) {
      register = reader.getRegister();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putRegister(register);
    }

    public String toString() {
      return "VariableInit " + register;
    }
  }

  
  public static final class DisplayPartClear extends Command {

    public int x;
    public int y;

    public int length;
    public byte dispAttr;

    private DisplayPartClear() {
      super(Cmd.DisplayPartClear);
    }

    public DisplayPartClear(HTCReader reader) {
      this();
      read(reader);
    }

    public DisplayPartClear(int y, int x, int length) {
      this();
      this.x = x;
      this.y = y;
      this.length = length;
    }

    protected void read(HTCReader reader) {
      x = reader.getInt(2);
      y = reader.getInt(2);
      length = reader.getInt(2);
      dispAttr = reader.getInt(1) == 1? PHL.DISPATTR_QUARTER:0;
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putInt(x, 2);
      writer.putInt(y, 2);
      writer.putInt(length, 2);
      writer.putInt(dispAttr == PHL.DISPATTR_QUARTER? 1:0, 1);
    }

    public String toString() {
      return "DisplayPartClear " + x + "," + y + "," + length + "," + dispAttr;
    }
  }

  /** アップ・ダウンロード */
  public static class UpDownload extends Command {

    /** ファイル名。ダウンロード時は使われないらしい */
    public Filename filename;

    /** ゲージ種類 */
    public int gaugeKind;

    /** ゲージ行 */
    public int gaugeLine;

    /** 中断キー */
    public Key stopKey = Key.F1;

    /** アップロードダウンロード */
    private UpDownload(Cmd cmd) {
      super(cmd);
    }

    public UpDownload(Cmd cmd, HTCReader reader) {
      this(cmd);
      read(reader);
    }

    protected void read(HTCReader reader) {
      filename = reader.getFilename();
      gaugeKind = reader.getInt(1);
      gaugeLine = reader.getInt(1);
      stopKey = reader.getKey();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putFilename(filename);
      writer.putInt(gaugeKind, 1);
      writer.putInt(gaugeLine, 1);
      writer.putKey(stopKey);
    }
  }

  /** ファイルアップロード */
  
  public static final class CommUpload extends UpDownload {
    private CommUpload() {
      super(Cmd.CommUpload);
    }

    public CommUpload(HTCReader reader) {
      this();
      read(reader);
    }

    public CommUpload(Filename filename) {
      this();
      this.filename = filename;
    }
    public String toString() {
      return "CommUpload filename=" + filename + ", gaugeKind=" + gaugeKind +
      ", gaugeLine=" + gaugeLine + ", stopKey=" + stopKey;
    }
  }

  /** ファイルダウンロード */
  
  public static final class CommDownload extends UpDownload {
    public CommDownload() {
      super(Cmd.CommDownload);
    }
    public CommDownload(HTCReader reader) {
      this();
      read(reader);
    }
    public String toString() {
      return "CommDownload " + gaugeKind + "," + gaugeLine + "," + stopKey;
    }
  }

  /** ファイル削除。RSLT:0...成功、RSLT:-1...失敗 */
  
  public static final class FileDelete extends Command {
    public Filename filename;

    private FileDelete() {
      super(Cmd.FileDelete);
    }

    public FileDelete(HTCReader reader) {
      this();
      read(reader);
    }

    public FileDelete(Filename filename) {
      this();
      this.filename = filename;
    }

    protected void read(HTCReader reader) {
      filename = reader.getFilename();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putFilename(filename);
    }

    public String toString() {
      return "FileDelete " + filename;
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // 編集
  /////////////////////////////////////////////////////////////////////////////


  /** 文字列コピー */
  
  public static final class Assign extends Command {

    private final static EnumSet<Register>inhibitRegs;
    static {
      inhibitRegs = EnumSet.noneOf(Register.class);
      inhibitRegs.add(Register.RSLT);
      inhibitRegs.add(Register.BRCOD);
      inhibitRegs.add(Register.NUMBR);
      inhibitRegs.add(Register.ENDKY);
    }

    public Register dst;
    public Object src;

    private Assign() {
      super(Cmd.Assign);
    }

    public Assign(HTCReader reader) {
      this();
      read(reader);
    }

    public Assign(Register dst, Object src) {
      this();
      assert(dst != null && !dst.system());
      this.dst = dst;
      this.src = src;

      if (src instanceof Register) {
        assert(!inhibitRegs.contains(src));
      } else if (src instanceof String) {
        src = new SJIS((String)src);
      } else if (src instanceof SJIS) {
        src = (SJIS)src;
      } else {
        // 本来srcにはレジスタか文字列しか指定できないが、
        // ここでは便宜的にそのほかのオブジェクトも指定できるようにしてある。
        // その際、強制的に文字列に変換する。
        src = new SJIS("" + src);
      }
    }

    protected void read(HTCReader reader) {
      dst = reader.getRegister();
      SJIS srcBytes = reader.getAllBytes();

      // srcはレジスタか？
      Register register = Register.findRegister(srcBytes.trim());
      if (register != null) {
        // srcがレジスタの場合、その型がdstと一致しない場合も可
        //assert(dst.type() == register.type());
        src = register;
        return;
      }

      // srcは値である。dstの型に一致するよう変換する。
      src = dst.type().convert(srcBytes);
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putRegister(dst);
      if (src instanceof Register)
        writer.putRegister((Register)src);
      else
        writer.putSJIS(new SJIS("" + src));
    }

    public String toString() {
      return "" + cmd + " dst=" + dst + ", src=" + src;
    }
  }

  /** 文字列連結。
   * <p>
   * 文字列の長さを知る方法がない。
   * 想像するに、このシステムでは「文字列の長さ」というものが保持されておらず、
   * 常に550バイトの領域が確保されているらしい。
   * 使われていない部分は空白で埋められているものと思われる。
   * </p>
   * <p>
   * したがって、文字列連結と言っても、実際には連結ではなく、二つの文字列で
   * コピー先領域を書き換えるイメージ。
   * </p>
   * <p>
   * 元々は１ベースの文字位置だが、使いずらいので０ベースにする
   * </p>
   */
  
  public static final class StringConcat extends Command {

    // コピー元１のレジスタ、位置、サイズ
    public Register srcReg1;
    public int srcPos1;
    public int srcSize1;

    // コピー元２のレジスタ、位置、サイズ
    public Register srcReg2;
    public int srcPos2;
    public int srcSize2;

    // コピー先のレジスタ、位置
    public Register dstReg;
    public int dstPos;

    private StringConcat() {
      super(Cmd.StringConcat);
    }

    public StringConcat(HTCReader reader) {
      this();
      read(reader);
    }

    public StringConcat(
        Register dst, int dstPos, Register srcReg1,
        int srcPos1, int srcSize1,
        Register srcReg2, int srcPos2, int srcSize2) {
      this();
      assert(srcReg1.type() == Type.STRING &&
          dst.type() == Type.STRING &&
          !dst.system());
      if (srcReg2 != null) {
        assert(srcReg2.type() == Type.STRING);
      }

      this.srcReg1 = srcReg1;
      this.srcPos1 = srcPos1;
      this.srcSize1 = srcSize1;
      this.srcReg2 = srcReg2;
      this.srcPos2 = srcPos2;
      this.srcSize2 = srcSize2;
      this.dstReg = dst;
      this.dstPos = dstPos;
    }

    /** HTCファイルからの読込。文字列位置を０ベースに変更する。 */
    public void read(HTCReader reader) {
      srcReg1 = reader.getRegister();
      srcPos1 = reader.getInt(3) - 1; // ０ベースに
      srcSize1 = reader.getInt(3);
      srcReg2 = reader.getRegister();
      srcPos2 = reader.getInt(3) - 1; // ０ベースに
      srcSize2 = reader.getInt(3);
      dstReg = reader.getRegister();
      dstPos = reader.getInt(3) - 1; // ０ベースに
    }

    /** HTCファイルへの書込み。文字列位置を１ベースに変更する */
    @Override
    public void write(HTCWriter writer) {
      writer.putRegister(srcReg1);
      writer.putInt(srcPos1 + 1, 3);
      writer.putInt(srcSize1, 3);
      writer.putRegister(srcReg2);
      writer.putInt(srcPos2 + 1, 3);
      writer.putInt(srcSize2, 3);
      writer.putRegister(dstReg);
      writer.putInt(dstPos + 1, 3);
    }

    public String toString() {
      return "StringConcat " +
        "dst:" + dstReg + "," + dstPos + " " +
        "src1:" + srcReg1 + "," + srcPos1 + "," + srcSize1 + " " +
        "src2:" + srcReg2 + "," + srcPos2 + "," + srcSize2;
    }
  }

  /** 抽出コピー。
   * dst,srcともにDATレジスタ
   */
  
  public static final class ExtractCopy extends Command {

    public Register dst;
    public int dstIndex;
    public Register src;
    public int srcIndex;
    public int srcSize;

    private ExtractCopy() {
      super(Cmd.ExtractCopy);
    }

    public ExtractCopy(HTCReader reader) {
      this();
      read(reader);
    }

    public ExtractCopy(Register dst, int dstIndex, Register src, int srcIndex, int srcSize) {
      this();
      this.dst = dst;
      this.dstIndex = dstIndex;
      this.src = src;
      this.srcIndex = srcIndex;
      this.srcSize = srcSize;
      assert(Register.datSet.contains(dst) && Register.datSet.contains(src));

    }

    protected void read(HTCReader reader) {
      src = reader.getRegister();
      srcIndex = reader.getInt(3) - 1;
      srcSize = reader.getInt(3);
      dst = reader.getRegister();
      dstIndex = reader.getInt(3) - 1;
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putRegister(src);
      writer.putInt(srcIndex + 1, 3);
      writer.putInt(srcSize, 3);
      writer.putRegister(dst);
      writer.putInt(dstIndex + 1, 3);
    }

    public String toString() {
      return "ExtractCopy " + dst + "," + dstIndex + "," + src +
        "," + srcIndex + "," + srcSize;
    }
  }

  /** 文字列シフト（文字埋め）。
   * どうもよくわからない。有意な文字列のサイズは不明のはずなので、
   * 右詰・左詰ができるはずもないが、有意はデータではないと見なしているのであろうか？ */
  
  public static final class StringShift extends Command {

    /** 対象レジスタ */
    public Register register;

    /** 影響を受けるサイズ */
    public int size;

    /** false:左詰、true:右詰 */
    public boolean right;

    /** 埋め込み文字 */
    public byte c;

    /** 作成 */
    private StringShift() {
      super(Cmd.StringShift);
    }

    public StringShift(HTCReader reader) {
      this();
      read(reader);
    }

    public StringShift(Register register, int size) {
      this(register, size, false);
    }

    public StringShift(Register register, int size, boolean right) {
      this(register, size, right, (byte)0x20);
    }

    public StringShift(Register register, int size, boolean right, byte c) {
      this();
      assert(register.type() == Type.STRING);
      this.register = register;
      this.size = size;
      this.right = right;
      this.c = c;
    }

    /** 読込 */
    protected void read(HTCReader reader) {
      register = reader.getRegister();
      size = reader.getInt(3);
      right = reader.getBytes(1).byteAt(0) != 'L';
      c = reader.getBytes(1).byteAt(0);
    }

    /** 書込み */
    @Override
    public void write(HTCWriter writer) {
      writer.putRegister(register);
      writer.putInt(size, 3);
      if (!right) writer.putSJIS(new SJIS("L"));
      else        writer.putSJIS(new SJIS("R"));
      writer.padding(c, 1);
    }

    /** 文字列化 */
    public String toString() {
      return "StringShift " + register + "," + size + "," + right + "," + c;
    }
  }

  /** 数値から文字列へ変換 */
  
  public static final class NumberToString extends Command {

    /** DAT */
    public Register dst;

    /** INT or FLT */
    public Register src;

    private NumberToString() {
      super(Cmd.NumberToString);
    }

    public NumberToString(HTCReader reader) {
      this();
      read(reader);
    }

    public NumberToString(Register dst, Register src) {
      this();
      assert(dst.type() == Type.STRING && !dst.system() && src != null
          && (Register.intSet.contains(src) || Register.fltSet.contains(src)));
      this.dst = dst;
      this.src = src;
    }

    protected void read(HTCReader reader) {
      dst = reader.getRegister();
      src = reader.getRegister();
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putRegister(dst);
      writer.putRegister(src);
    }

    public String toString() {
      return "" + cmd + " " + dst + " " + src;
    }
  }

  /** ウェイト実行 */
  
  public static final class WaitMS extends Command {

    public int ms = 100;

    private WaitMS() {
      super(Cmd.WaitMS);
    }

    public WaitMS(HTCReader reader) {
      this();
      ms = reader.getInt(4);
    }

    public WaitMS(int ms) {
      this();
      this.ms = ms;
    }

    @Override
    public void write(HTCWriter writer) {
      writer.putInt(ms, 4);
    }

    public String toString() {
      return "" + cmd + " " + ms;
    }
  }
}
