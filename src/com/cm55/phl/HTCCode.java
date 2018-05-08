// Created by Cryptomedia Co., Ltd. 2006/06/03
package com.cm55.phl;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.cm55.phl.Command.*;
import com.cm55.phl.PHL.*;

/**
 * PHLプログラムコード
 */
public class HTCCode  {

  //private static final CLog log = CLogFactory.get(HTCCode.class);

  /** コードバイト */
  protected byte[]codeBytes;

  /** タイトル */
  protected Title title;

  /** コマンド列 */
  protected ArrayList<Command>commandList = new ArrayList<Command>();

  /** 指定された入力ストリームからコンパイル済みコードを読み込む */
  public HTCCode(InputStream in) throws IOException {

    codeBytes = Utils.getWholeBytes(in);

    ByteArrayInputStream bin = new ByteArrayInputStream(codeBytes);
    HTCReader reader = new HTCReader(bin);

    // タイトル読込
    readTitle(reader);

    // ラベル読込
    Map<Integer,Label>numberToLabel = new HashMap<Integer,Label>();
    Map<Integer,Label[]>offsetToLabels = new HashMap<Integer,Label[]>();
    while (true) {
      SJIS head = reader.getBytes(2);
      if (!head.equals(new SJIS("B "))) {
        reader.unget(2);
        break;
      }
      readLabel(reader, numberToLabel, offsetToLabels);
    }

    // コマンド読込
    int bodyStart = reader.getPosition();
    while (true) {

      int offset = reader.getPosition() - bodyStart;

      // このオフセット位置のラベルを見つけ、コマンドリストに追加する
      Label[]labels = offsetToLabels.remove(offset);
      if (labels != null) {
        for (Label label: labels) commandList.add(label);
      }

      // コマンドの解析
      commandList.add(readCommand(reader));

      if (reader.eof()) break;
    }

    // すべてのラベルオフセットについて処理されたか
    if (offsetToLabels.size() > 0) {
      for (Map.Entry<Integer,Label[]>e: offsetToLabels.entrySet()) {
        System.err.println("" + e.getKey());
        for (Label label: e.getValue()) {
          System.err.println(" " + label.getNumber());
        }
      }
      reader.readException("オフセットのわからないラベルがあります:" +
          offsetToLabels.size());
    }

    // ラベルにコマンド列中のインデックスをつける、ジャンプにラベルを設定する。
    for (int i = 0; i < commandList.size(); i++) {
      Command command = commandList.get(i);

      if (command instanceof Label) {
        ((Label)command).setIndex(i);
        continue;
      }

      if (command instanceof AbstractJump) {
        AbstractJump jump = (AbstractJump)command;
        jump.setTargetLabel(numberToLabel.get(jump.labelNumber));
        continue;
      }
    }
  }

  /** タイトル行を読み込む */
  protected void readTitle(HTCReader reader) {
    //if (log.ist()) log.trace("title:" + reader);
    SJIS head = reader.getBytes(2);
    if (!head.equals(Cmd.Title.head())) {
      reader.parseException();
    }
    title = new Title(reader);
    reader.endOfLine();
  }

  /** ラベルを読み込む。ラベルでなければfalseを返す */
  protected void readLabel(HTCReader reader,
      Map<Integer,Label>numberToLabel,
      Map<Integer,Label[]>offsetToLabels) {

    // ラベル作成
    Label label = new Label();
    label.read(reader);
    reader.endOfLine();

    if (numberToLabel.containsKey(label.getNumber())) {
      assert(numberToLabel.get(label.getNumber()).getOffset() == label.getOffset());
    }

    // ラベル番号/ラベルマップに登録
    numberToLabel.put(label.getNumber(), label);

    // オフセット/ラベル配列マップに登録
    Label[]labels = offsetToLabels.get(label.getOffset());
    if (labels == null) {
      // 未登録の場合、これ一つだけ
      offsetToLabels.put(label.getOffset(), new Label[] { label });
      return;
    }

    // 既に登録がある場合、複数
    ArrayList<Label>labelList = new ArrayList<Label>(Arrays.asList(labels));
    labelList.add(label);
    offsetToLabels.put(label.getOffset(), labelList.toArray(new Label[0]));
  }

  /** コマンドを解析する */
  @SuppressWarnings("unchecked")
  protected Command readCommand(HTCReader reader) {

    //if (log.ist()) log.trace("  " + reader);

    // 最初の３桁はコマンド種類
    SJIS cmdBytes = reader.getBytes(3);
    Cmd cmd = Cmd.findCommand(cmdBytes);
    if (cmd == null) {
      reader.readException("未サポートのコマンドです：" + cmdBytes);
    }
    //if (log.ist())      log.trace("  " + cmd + "," + reader);

    // コマンドオブジェクトを生成すると同時にパラメータを読み込ませる
    Command command = null;
    try {
      Constructor<? extends Command> cons = cmd.clazz.getConstructor(HTCReader.class);
      command = cons.newInstance(reader);
    } catch (Exception ex) {
      reader.readException(
          "プログラムエラー：" + cmd.clazz + "のコンストラクタが異常です");
    }
    reader.endOfLine();

    return command;
  }

  /** タイトル取得 */
  public Title getTitle() {
    return title;
  }

  /** コマンド数取得 */
  public int numCommands() {
    return commandList.size();
  }

  /** コマンド取得 */
  public Command getCommand(int index) {
    return commandList.get(index);
  }

  /** テキストの形で出力 */
  public void outputTxt(OutputStream out) throws IOException {
    PrintStream stream = new PrintStream(out, false, SJIS.ENCODING);
    for (Command command: commandList) {
      if (command instanceof Label)
        stream.println(command.toString());
      else
        stream.println("  " + command.toString());
    }
  }

  /** コードを出力 */
  public void outputHtc(OutputStream out) throws IOException {
    out.write(codeBytes);
  }
}
