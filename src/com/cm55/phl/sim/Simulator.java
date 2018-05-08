// Created by Cryptomedia Co., Ltd. 2006/06/06
package com.cm55.phl.sim;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import com.cm55.phl.*;
import com.cm55.phl.Command.*;
import com.cm55.phl.PHL.*;
import com.cm55.phl.sim.FileArea.*;

/**
 * PHLシミュレータ
 */
public class Simulator extends Thread {

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface MethodFor { 
    Class<? extends Command>cls();
  }

  static Map<Class<? extends Command>, Method>methodMap = new HashMap<>();
  static {
    for (Method method: Simulator.class.getDeclaredMethods()) {
      MethodFor methodFor = method.getAnnotation(MethodFor.class);
      if (methodFor == null) continue;
      methodMap.put(methodFor.cls(), method);
    }
  }

  /** プログラムコード */
  protected HTCCode code;

  /** プログラムカウンタ */
  protected int pc;

  /** ファイルエリア */
  protected FileArea fileArea;

  /** フレームバッファ */
  public FrameBuffer frameBuffer;

  /** レジスター値マップ */
  protected RegisterStore regStore;

  /** 中断 */
  protected boolean terminated;

  /** 作成する */
  public Simulator(HTCCode code, FileArea fileArea, FrameBuffer frameBuffer,
      RegisterStore regStore) {

    // if (log.ist()) log.trace("run " + code);

    this.code = code;
    this.fileArea = fileArea;
    this.frameBuffer = frameBuffer;
    this.regStore = regStore;

    pc = 0;

  }

  public HTCCode getCode() {
    return code;
  }

  /** シミュレータを中断する */
  public synchronized void terminate() {
    terminated = true;
    interrupt();
  }

  /** コードを実行する */
  public void run() {

    // タイトル処理
    title(code.getTitle());

    // コマンド処理
    while (!terminated) {
      Command command = code.getCommand(pc++);
      Class<? extends Command> argClass = command.cmd.clazz;
      
      /* if (log.ist()) {
        log.trace("cmdstr:" + cmdstr + 
            ", methodName:" + methodName + 
            ", argClass:" + argClass);
      }
      */
      
      try {
        Method method = methodMap.get(argClass);
        method.invoke(this, command);
      } catch (Exception ex) {
        ex.printStackTrace();
        assert(false);
      }

    }
  }

  /** タイトル処理 */
  @MethodFor(cls=Title.class)
  protected void title(Title command) {

    if (command.startScreen == StartScreen.NONE) return;

    int numColumns = frameBuffer.numColumns();
    displayClear(null);

    // タイトル表示
    SJIS title = command.title;
    int x = (numColumns - title.length()) / 2;
    frameBuffer.drawSJIS(3, x, title, (byte)0);

    // バージョン表示
    SJIS version = new SJIS("Ver ").append(command.version);
    x = (numColumns - version.length()) / 2;
    frameBuffer.drawSJIS(5, x, version, (byte)0);


    switch (command.startScreen) {
    case TWOSECONDS:
      try {
        Thread.sleep(2000);
      } catch (InterruptedException ex) {
      }
      break;
    case ANYKEY:
      input();
      break;
    default: break;
    }
  }

  /** ラベル。何もしない */
  @MethodFor(cls=Label.class)
  protected void label(Label comand) {
  }

  /** バーコード情報。何もしない */
  @MethodFor(cls=BarcodeInfo.class)
  protected void barcodeInfo(BarcodeInfo command) {
  }

  /** 全画面クリア */
  @MethodFor(cls=DisplayClear.class)
  protected void displayClear(DisplayClear command) {
    // if (log.ist()) log.trace("displayClear");
    frameBuffer.clearAll();
  }

  /** 画面部分クリア */
  @MethodFor(cls=DisplayPartClear.class)
  protected void displayPartClear(DisplayPartClear command) {
    // if (log.ist()) log.trace("" + command);
    frameBuffer.clearPart(command.y, command.x, command.length, command.dispAttr);

  }

  @MethodFor(cls=DisplayString.class)
  protected void displayString(DisplayString command) {
    display(command);
  }

  @MethodFor(cls=DisplayRegister.class)
  protected void displayRegister(DisplayRegister command) {
    display(command);
  }

  /** 画面表示 */
  @MethodFor(cls=Display.class)
  protected void display(Display command) {
    // if (log.ist()) log.trace("" + command);
    if (command.clearBytes > 0)
      frameBuffer.clearPart(command.y, command.x, command.clearBytes, (byte)0);

    SJIS sjis = null;

    if (command instanceof DisplayString) {
      // 文字列表示
      DisplayString dispStr = (DisplayString)command;
      sjis = dispStr.sjis;

    } else if (command instanceof DisplayRegister) {
      // レジスター表示
      DisplayRegister dispReg = (DisplayRegister)command;
      try {
        Object value = regStore.getValue(dispReg.getRegister());
        if (value instanceof SJIS) sjis = (SJIS)value;
        else                        sjis = new SJIS(value.toString());
      } catch (Exception ex) {
      }
      sjis = sjis.forceSize(dispReg.getStart() + dispReg.getLength());
      sjis = sjis.extract(dispReg.getStart(), dispReg.getLength());
    }

    // フレームバッファにコピー
    frameBuffer.drawSJIS(command.y, command.x, sjis, (byte)0);
  }

  /** エコー無し入力 */
  @MethodFor(cls=NoEchoInput.class)
  protected void noEchoInput(NoEchoInput command) {
    // if (log.ist()) log.trace("noEchoInput " + command);

    while (true) {
      int value = input();
      if (terminated) return;
      Key key = Key.findKey(value);
      if (key == null) {
        beep();
        continue;
      }
      regStore.setValue(command.register, value);
      return;
    }
  }

  /** 条件付きジャンプ */
  @SuppressWarnings("unchecked")
  @MethodFor(cls=JumpIf.class)
  protected void jumpIf(JumpIf command) {

    // if (log.ist())      log.trace(" " + command);

    // 比較対象レジスタの値を取得する
    @SuppressWarnings("rawtypes")
    Comparable leftValue = regStore.getValue(command.register);

    // もし右側がレジスタであればその値を取得する
    Object rightValue = command.value;
    if (command.value instanceof Register)
      rightValue = regStore.getValue((Register)command.value);

    // 右側の値を比較可能なオブジェクトに変換
    rightValue = command.register.type().convert(rightValue);

    // if (log.ist())       log.trace("left/right " + leftValue + ", " + rightValue);
    

    // 比較実行
    int result = leftValue.compareTo(rightValue);

    // ジャンプするか否かを決定
    boolean jump = false;
    switch (command.comp) {
    case EQ: jump = result == 0; break;
    case LT: jump = result < 0; break;
    case GT: jump = result > 0; break;
    case LE: jump = result <= 0; break;
    case GE: jump = result >= 0; break;
    case NE: jump = result != 0; break;
    }

    // ジャンプ実行
    if (jump) {
      pc = command.getTargetLabel().getIndex();
    }
  }

  /** ジャンプ */
  @MethodFor(cls=Jump.class)
  protected void jump(Jump command) {
    // if (log.ist()) log.trace("jump " + command);
    //pc = code.getBranchIndex(command.labelNumber);
    pc = command.getTargetLabel().getIndex();
  }

  @MethodFor(cls=EchoedInput.class)
  protected void echoedInput(EchoedInput command) {
    lineInput(command);
  }

  @MethodFor(cls=BarcodeInput.class)
  protected void barcodeInput(BarcodeInput command) {
    lineInput(command);
  }

  /** 行入力 */
  @MethodFor(cls=LineInput.class)
  protected void lineInput(LineInput command) {
    // if (log.ist()) log.trace("lineInput " + command);

    boolean noEcho = false;
    if (command instanceof EchoedInput) {
      noEcho = ((EchoedInput)command).noEcho;
    }

    // 入力バッファ
    SJIS buffer = new SJIS(0);

    // カーソル表示モード
    CursorShape cursorShape = CursorShape.NONE;
    if (!noEcho) {
      if (command instanceof EchoedInput) {
        cursorShape = ((EchoedInput)command).cursor;
      } else { // BarcodeInput
        if (((BarcodeInput)command).underbarCursor)
          cursorShape = CursorShape.UNDERBAR;
      }
    }

  loop:
    while (true) {

      // カーソル表示
      if (cursorShape != CursorShape.NONE) {
        int offset = Math.min(buffer.length(), command.columns - 1);
        frameBuffer.cursorOn(command.y, command.x + offset);
      }

      // キー入力の取得
      int value = input();
      // if (log.ist()) log.trace("input value " + value);

      if (terminated) {
        frameBuffer.cursorOff();
        return;
      }
      Key key = Key.findKey(value);
      if (key == null) continue;

      // コマンド指定の特殊キーの場合
      // 返り値をセットして終了
      if (command.specialKeys.contains(key)) {
        // if (log.ist())          log.trace("is specialKey " + key);
        regStore.setSystem(Register.RSLT, key.code());
        regStore.setSystem(Register.ENDKY, 1);
        regStore.setSystem(Register.NUMBR, 0);

        frameBuffer.cursorOff();
        return;
      }

      // その他の特殊キーの処理
      switch (key) {
      case BS:
        if (buffer.length() == 0) continue;
        buffer = buffer.removeLast();
        frameBuffer.clearPart(command.y, command.x + buffer.length(), 1, (byte)0);
        continue;
      case CLR:
        if (buffer.length() == 0) continue;
        frameBuffer.clearPart(command.y, command.x, buffer.length(), (byte)0);
        buffer = new SJIS(0);
        break;
      case ENT:
        break loop;
      default: break;
      }

      // バッファが一杯か
      if (buffer.length() >= command.columns) {
        beep();
        continue;
      }

      // 入力可能な文字のチェック
      if (value < 0x20 || 0x7F <= value) {
        beep();
        continue;
      }
      switch (command.register.type()) {
      case STRING:
        if (key != null && !key.hasGlyph()) {
          beep();
          continue;
        }
        break;
      case INTEGER:
        if (value < '0' || '9' < value) {
          beep();
          continue;
        }
        break;
      case FLOAT:
         if (('0' <= value &&  value <= '9') || value == '.') {
           // OK
         } else {
           beep();
          continue;
        }
        break;
      }

      // バッファへ入力
      // if (log.ist()) log.trace("enter to buffer " + buffer);
      buffer = buffer.append(new SJIS(1, (byte)value));
      // if (log.ist()) log.trace("" + buffer);

      if (!noEcho) {
        frameBuffer.drawSJIS(command.y, command.x + buffer.length() - 1,
          new SJIS(1, (byte)value), (byte)0);
      }

      // エコー付入力の場合、自動次項目移行
      if (command instanceof EchoedInput && buffer.length() == command.columns) {
        switch (((EchoedInput)command).fullAction) {
        case IMMEDIATE: // すぐ
          break loop;
        case ONESECOND: // 1秒後
          try {
            Thread.sleep(1000);
          } catch (InterruptedException ex) {
          }
          break loop;
        default: break;
        }
      }
    } // while

    // 値を変数に設定
    switch (command.register.type()) {
    case STRING:
      regStore.setValue(command.register, buffer);
      break;
    default:
      regStore.setValue(command.register, buffer.toString());
    }
    regStore.setSystem(Register.RSLT, 0);
    regStore.setSystem(Register.ENDKY, 0);
    regStore.setSystem(Register.NUMBR, buffer.length());
    frameBuffer.cursorOff();
  }


  /** 数値を文字列化 */
  @MethodFor(cls=NumberToString.class)
  protected void numberToString(NumberToString command) {
    // if (log.ist()) log.trace("" + command.toString());

    String s = "" + regStore.getValue(command.src);
    regStore.setValue(command.dst, new SJIS(s));
    regStore.setSystem(Register.RSLT, s.length());

    // if (log.ist()) log.trace("[" + s + "]");
  }

  /** 文字列のシフト。文字列変数のみ */
  @MethodFor(cls=StringShift.class)
  protected void stringShift(StringShift command) {
    // if (log.ist()) log.trace("" + command);

    SJIS sjis = regStore.getValue(command.register);
    if (sjis.length() > command.size) {
      sjis = sjis.extract(0, command.size);
    } else {
      SJIS fill = new SJIS(command.size - sjis.length(), command.c);
      if (!command.right) sjis = sjis.append(fill);
      else                sjis = fill.append(sjis);
    }
    regStore.setValue(command.register, sjis);

    // if (log.ist()) log.trace("[" + sjis + "]");
  }

  /** マスタ検索。見つからなかった場合は指定された変数をクリアするらしい */
  @MethodFor(cls=MasterSearch.class)
  protected void masterSearch(final MasterSearch command) {
    // if (log.ist()) log.trace("" + command);

    final MemoryFile file = fileArea.getFile(getFilenameSJIS(command.filename));
    if (file == null) {
      // if (log.ist()) log.trace("  no file");
      regStore.initValue(command.resultReg);
      regStore.setSystem(Register.RSLT, -1);
      return;
    }

    final int recordCount = file.recordCount(command.recordLen);

    // if (log.ist()) log.trace("  recordCount " + recordCount);
    //ystem.err.println("recordCount " + recordCount);

    Slots<SJIS> slotArray = new Slots<SJIS>() {
      public int size() {
        return recordCount;
      }
      public SJIS get(int index) {
        boolean positionResult = file.position(command.recordLen, index);
        assert positionResult;
        SJIS sjis = file.read(command.recordLen);
        SJIS result = sjis.extract(command.keyPos1, command.keySize1);
        if (command.keyReg2 != null) {
          result.append(sjis.extract(command.keyPos2, command.keySize2));
        }
        // if (log.ist()) log.trace("result:" + result);
        
        return result;
      }
    };

    SJIS key = regStore.getValue(command.keyReg1);
    key = key.forceSize(command.keySize1);
    if (command.keyReg2 != null) {
      SJIS key2 = regStore.getValue(command.keyReg2);
      key2 = key2.forceSize(command.keySize2);
      key.append(key2);
    }

    // if (log.ist()) log.trace("  key " + key);

    int index = FileArea.binarySearch(
        slotArray,
        key,
        new Comparator<SJIS>() {
          public int compare(SJIS a, SJIS b) {

            int r = a.compareTo(b);
            // if (log.ist()) log.trace("  compare " + a + "," + b + "," + r);
            return r;
          }
        }
    );

    // if (log.ist()) log.trace("  index " + index);

    if (index < 0) {
      regStore.initValue(command.resultReg);
      regStore.setSystem(Register.RSLT, -2);
      return;
    }

    assert(file.position(index, command.recordLen));
    regStore.setValue(command.resultReg, file.read(command.recordLen));
    regStore.setSystem(Register.RSLT, 0);
  }

  /** 変数値のコピー。src,dstともに任意の型の変数 */
  @MethodFor(cls=Assign.class)
  protected void assign(Assign command) {
    // if (log.ist()) log.trace(command.toString());

    // srcを取得する
    Object src = command.src;

    // srcがレジスタの場合、その型はdstと一致しなくてもよい
    if (src instanceof Register) {
      // srcレジスタの値を取得
      src = regStore.getValue((Register)src);

      // dstの型と一致しない場合は一律に文字列に変換
      if (!command.dst.type().isCompatible(src))
        src = src.toString();
    }

    // 文字列を押し込む
    regStore.setValue(command.dst, src);
  }

  /** 文字列変数値の接続。src,dstともに文字列変数のみ */
  @MethodFor(cls=StringConcat.class)
  protected void stringConcat(StringConcat command) {
    // if (log.ist()) log.trace(command.toString());

    SJIS src1Value = regStore.getValue(command.srcReg1);
    SJIS src2Value = regStore.getValue(command.srcReg2);
    SJIS dstValue = regStore.getValue(command.dstReg);

    int src1Avail = Math.min(
        src1Value.length() - command.srcPos1,
        command.srcSize1
    );
    int src2Avail = Math.min(
        src2Value.length() - command.srcPos2,
        command.srcSize2
    );
    int total = src1Avail + src2Avail;

    if (dstValue.length() < command.dstPos + total) {
      dstValue = dstValue.append(
          new SJIS(command.dstPos + total - dstValue.length())
      );
    }

    // if (log.ist())      log.trace("values ... " + src1Value + "," + src2Value + "," + dstValue);

    int dstIndex = command.dstPos;
    dstValue = dstValue.replace(dstIndex,
        src1Value.extract(command.srcPos1, src1Avail));
    dstIndex += src1Avail;
    dstValue = dstValue.replace(dstIndex,
        src2Value.extract(command.srcPos2, src2Avail));

    regStore.setValue(command.dstReg, dstValue);
  }

  /** 抽出コピー */
  @MethodFor(cls=ExtractCopy.class)
  protected void extractCopy(ExtractCopy command) {
    // if (log.ist()) log.trace("" + command);

    SJIS srcValue = regStore.getValue(command.src);
    // if (log.ist()) log.trace("src:" + srcValue);

    srcValue = srcValue.forceSize(command.srcIndex + command.srcSize);

    srcValue = srcValue.extract(command.srcIndex, command.srcSize);

    SJIS dstValue = regStore.getValue(command.dst);
    // if (log.ist()) log.trace("dst:" + dstValue);

    if (dstValue.length() < command.dstIndex + srcValue.length()) {
      dstValue = dstValue.append(
          new SJIS(command.dstIndex + srcValue.length() - dstValue.length())
      );
    }

    dstValue = dstValue.replace(command.dstIndex, srcValue);
    regStore.setValue(command.dst, dstValue);

    // if (log.ist())      log.trace("  [" + dstValue + "]");
  }

  /** レコードの書き込み。書き込み元は文字列変数のみ */
  @MethodFor(cls=RecordWrite.class)
  protected void recordWrite(RecordWrite command) {
    // if (log.ist()) log.trace("" + command);

    // if (log.ist())      log.trace("[" + regStore.getValue(command.register).toString()+ "]");

    MemoryFile file = fileArea.ensureFile(getFilenameSJIS(command.filename));
    /*
    file.write(
        (SJIS)varStore.getRegisterValue(command.register),
        command.recordLen,
        command.crlf,
        command.overwrite
    );
    */

    SJIS sjis = regStore.getValue(command.register);
    sjis = sjis.forceSize(command.recordLen);
    if (command.crlf) sjis = sjis.append(new SJIS("\r\n"));
    if (command.overwrite) {
      file.write(sjis);
    } else {
      file.append(sjis);
    }
  }

  /** レコードの読込 */
  @MethodFor(cls=RecordRead.class)
  protected void recordRead(RecordRead command) {
    // if (log.ist()) log.trace("" + command);

    MemoryFile file = fileArea.getFile(getFilenameSJIS(command.filename));
    if (file == null) {
      regStore.setSystem(Register.RSLT, -1);
      return;
    }

    boolean result = true;
    switch (command.filePos) {
    case PREV: result = file.previous(command.recordLen); break;
    case NEXT: result = file.next(command.recordLen); break;
    case TOP:  result = file.top(command.recordLen); break;
    case BOT:  result = file.bottom(command.recordLen); break;
    default: break;
    }

    // 返値にかかわらずレコード読み取りは行われる（ようだ）。
    // eofの場合は空白文字列が返る。
    //if (result) {
      SJIS sjis = file.read(command.recordLen);
      assert(sjis != null);
      regStore.setValue(command.register, sjis);
    //}

    regStore.setSystem(Register.RSLT, result? 0:-2);
  }

  /** レコード数取得 */
  @MethodFor(cls=RecordCount.class)
  protected void recordCount(RecordCount command) {
    // if (log.ist()) log.trace("" + command);

    MemoryFile file = fileArea.getFile(getFilenameSJIS(command.filename));
    if (file == null) {
      regStore.setSystem(Register.RSLT, -1);
      return;
    }

    regStore.setValue(command.intReg, file.fileSize() / command.recordLen);
    regStore.setSystem(Register.RSLT, 0);
  }

  /** ファイルの存在チェック */
  @MethodFor(cls=FileExists.class)
  protected void fileExists(FileExists command) {
    // if (log.ist()) log.trace("" + command);

    regStore.setSystem(Register.RSLT,
        fileArea.getFile(getFilenameSJIS(command.filename)) == null? -1:0
    );
  }

  /** 変数初期化 */
  @MethodFor(cls=VariableInit.class)
  protected void variableInit(VariableInit command) {
    // if (log.ist()) log.trace("" + command);
    regStore.initValue(command.register);
  }

  /** ダウンロード */
  @MethodFor(cls=CommDownload.class)
  protected void commDownload(CommDownload command) {
    // if (log.ist()) log.trace("" + command);

    TransferFile file = new TransferFile();
    download(file);

    for (int i = 0; i < file.count(); i++) {
      fileArea.putFile(
          new SJIS(file.name(i)),
          file.bytes(0)
      );
    }

    regStore.setSystem(Register.RSLT, file.stopped? -2:0);
  }

  /** アップロード */
  @MethodFor(cls=CommUpload.class)
  protected void commUpload(CommUpload command) {
    // if (log.ist()) log.trace("" + command);

    MemoryFile memFile = fileArea.getFile(getFilenameSJIS(command.filename));
    if (memFile == null) {
      regStore.setSystem(Register.RSLT, -1);
      return;
    }

    TransferFile file = new TransferFile();
    file.put(memFile.filename.toString(), memFile.getBytes());
    upload(file);

    regStore.setSystem(Register.RSLT, file.stopped? -2:0);
  }

  /** ファイル削除 */
  @MethodFor(cls=FileDelete.class)
  protected void fileDelete(FileDelete command) {
    // if (log.ist()) log.trace("" + command);

    regStore.setSystem(Register.RSLT,
        fileArea.deleteFile(getFilenameSJIS(command.filename))? 0:-1);
  }


  public SJIS getFilenameSJIS(Filename filename) {
    if (filename.getSJIS() != null) return filename.getSJIS();
    return regStore.getValue(filename.getRegister());
  }

  /** ウェイト実行 */
  @MethodFor(cls=WaitMS.class)
  protected void waitMS(WaitMS command) {
    try {
      Thread.sleep(command.ms);
    } catch (InterruptedException ex) {
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // オーバーライド用
  /////////////////////////////////////////////////////////////////////////////


  /** ベル */
  protected void beep() {
  }

  protected void download(TransferFile file) {

  }

  protected void upload(TransferFile file) {

  }

  /** 入力文字 */
  protected ArrayList<Integer>inputChars = new ArrayList<Integer>();

  /** 文字入力 */
  protected synchronized int input() {
    while (true) {
      if (inputChars.size() == 0) {
        try {
          wait();
        } catch (Exception ex) {
          return 0;
        }
      }
      int value = inputChars.remove(0);

      return value;
    }
  }

  /** キャラクタをセット */
  public synchronized void setCharacter(int value) {
    inputChars.add(value);
    notify();
  }

  public static class TransferFile {
    ArrayList<String>names = new ArrayList<String>();
    ArrayList<byte[]>bytes = new ArrayList<byte[]>();
    public boolean stopped;

    public int count() { return names.size(); }
    public String name(int index) { return names.get(index); }
    public byte[] bytes(int index) { return bytes.get(index); }
    public void put(String name, byte[]b) {
      names.add(name.toLowerCase());
      bytes.add(b);
    }
  }
}
