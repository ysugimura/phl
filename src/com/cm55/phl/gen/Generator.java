// Created by Cryptomedia Co., Ltd. 2006/06/05
package com.cm55.phl.gen;

import java.io.*;
import java.util.*;

import com.cm55.phl.*;
import com.cm55.phl.Command.*;
import com.cm55.phl.PHL.*;

/**
 * PHLシリーズコードジェネレータ
 * <p>
 * タイトルコマンド、ボディ部を指定して作成する。
 * outputHTCでOutputStreamにHTCコードを出力する。
 * </p>
 */
public class Generator {

//  private static final CLog log = CLogFactory.get(Generator.class);

  /** 説明用文字列 */
  protected String description = "";

  /** タイトルコマンド */
  protected Title title;

  /** バーコード情報。デフォルト設定済み */
  protected BarcodeInfo[]barcodeInfo = new BarcodeInfo[] {
      new BarcodeInfo(1),
      new BarcodeInfo(2)
  };

  /** ボディ要素 */
  protected Object body;

  /** ユーザラベル番号としてこれ以降を使用 */
  private static final int USER_LABEL_NUMBER = 4;

  /** 作成する */
  public Generator(Title title, Object body) {
    this.title = title;
    this.body = body;
    assert(body instanceof Command || body instanceof Macro);
  }

  /** 説明 */
  public void setDescription(String s) {
    description = s;
  }

  /** バーコード情報を設定する */
  public void setBarcodeInfo1(BarcodeInfo[]b) {
    barcodeInfo = b;
  }

  /** HTCをファイルに出力する */
  public void outputHTC(File file) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      outputHTC(out);
    } finally {
      out.close();
    }
  }

  /** HTCを出力する */
  public void outputHTC(OutputStream out) throws IOException {

    // コマンド列を得る
    ArrayList<Command>commands = getCommands(true);

    // ボディ部コードを生成。ヘッダ部の前に行われる。
    byte[]bodyCode = genBodyCode(commands);

    // ヘッダ部コードを生成
    byte[]headCode = genHeadCode(commands);

    // 出力。上とは逆にヘッダ部、ボディ部の順に出力
    out.write(headCode);
    out.write(bodyCode);
  }

  /** 展開結果のコマンドリストを得る */
  public ArrayList<Command>getCommands(boolean optimized) {

    // 展開コンテキストを作成
    Context ctx = new Context(title.profile);

    // バーコード情報を設定
    for (BarcodeInfo bi: barcodeInfo)
      ctx.proc(bi);

    // ボディ部を展開
    ctx.proc(body);

    // コマンド列を取得
    ArrayList<Command>commands = ctx.getCommands();

    // ラベル・ジャンプの最適化
    if (optimized) optimizeLabelJumps(commands);

    // すべてのラベルに番号を設定する。
    int labelNumber = USER_LABEL_NUMBER;
    for (Command command: commands) {
      if (command instanceof Label)
        ((Label)command).setNumber(labelNumber++);
    }

    return commands;
  }

  /** ボディ部コードを生成する。
   * <p>
   * コード生成中に各ラベルの「ボディ部中オフセット」が得られるので、
   * ラベル情報を置くヘッダ部はこの後で生成しなければならない。
   * </p>
   */
  protected byte[]genBodyCode(ArrayList<Command>commands) {
    ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
    HTCWriter writer = new HTCWriter(bodyOut);
    for (Command command: commands) {

      // ラベルの場合、ボディ部位置を取得する。書き込みはしない。
      if (command instanceof Label) {
        Label label = (Label)command;
        label.setOffset(writer.getPosition());
        continue;
      }

      // ジャンプの場合、ラベル番号を取得する
      if (command instanceof AbstractJump) {
        AbstractJump jump = (AbstractJump)command;
        jump.setLabelNumber(jump.getTargetLabel().getNumber());
        // fall through
      }

      // コマンド書き込み
      command.write(writer);
      writer.endOfLine(command.cmd);
    }
    new Jump(1).write(writer); // 強制的にシステムに戻るジャンプを追加
    writer.endOfLine(Cmd.Jump);
    try { bodyOut.close(); } catch (Exception ex) {}
    return bodyOut.toByteArray();
  }

  /** ヘッダ部コードを生成する */
  protected byte[]genHeadCode(ArrayList<Command>commands) {
    ByteArrayOutputStream headOut = new ByteArrayOutputStream();
    HTCWriter writer = new HTCWriter(headOut);
    title.write(writer);
    writer.endOfLine(title.cmd);
    for (Command command: commands) {
      if (command instanceof Label) {
        command.write(writer);
        writer.endOfLine(command.cmd);
      }
    }
    try { headOut.close(); } catch (Exception ex) {}
    return  headOut.toByteArray();
  }

  /////////////////////////////////////////////////////////////////////////////
  // ラベル・ジャンプの最適化
  /////////////////////////////////////////////////////////////////////////////

  /** ラベル・ジャンプを最適化する。
   * マクロ展開の過程で不必要なラベル、ジャンプが生成されてしまう。
   * 例えば、同じ場所に複数のラベルがつけられていたり、無条件ジャンプのすぐ後に
   * 無条件ジャンプが作成されたりする。これらを削除してコードをすっきりさせる。
   * もちろん、これを行わなくとも動作に支障は無い。
   */
  protected void optimizeLabelJumps(ArrayList<Command>commands) {

    // ジャンプを補正。これはたぶん一度だけ行えばよい
    jumpCorrection(commands);

     boolean doit = true;
     while (doit) {
       doit = false;

       // 未使用ラベルを削除
       if (removeUnusedLabel(commands)) doit = true;

       // 無意味な無条件ジャンプを削除
       if (removeUnusedJump(commands)) doit = true;

       // すぐ下へのジャンプを削除
       if (removeNeighborJump(commands)) doit = true;
     }
  }

  /** ジャンプを補正する。
   * <p>
   *  ジャンプ先のラベルの後にまたラベルというように、ラベルが続いていたら、
   *  ジャンプ先を最も下のラベルに変更する。さらにその下が無条件ジャンプであれば、
   *  ジャンプ先をその無条件ジャンプ先に変更する。これを繰り返す。
   * </p>
   */
  protected boolean jumpCorrection(ArrayList<Command>commands) {

    boolean corrected = false;

    // すべてのラベルのインデックスを取得
    Map<Label, Integer>labelIndices = new HashMap<Label,Integer>();
    for (int i = 0; i < commands.size(); i++) {
      Command c = commands.get(i);
      if (c instanceof Label) labelIndices.put((Label)c, i);
    }

    // ジャンプ先を補正
    commandLoop:
    for (Command c: commands) {
      if (!(c instanceof AbstractJump)) continue;

      AbstractJump targetJump = (AbstractJump)c;
      while (true) {
        int index = labelIndices.get(targetJump.getTargetLabel());

        // 下方向にラベル以外のものを探す
        int lastLabelIndex = index;
        for (index++; index < commands.size(); index++) {
          if (commands.get(index) instanceof Label)
            lastLabelIndex = index;
          else
            break;
        }

        // 最後までラベルだったか、あるいは無条件ジャンプ以外が現れた
        // 最後のラベルにジャンプ先を変更
        if (commands.size() <= index || !(commands.get(index) instanceof Jump)) {
          Label newLabel = (Label)commands.get(lastLabelIndex);
          if (targetJump.getTargetLabel() != newLabel) {
            targetJump.replaceTargetLabel(newLabel);
            corrected = true;
          }
          continue commandLoop;
        }

        // 無条件ジャンプが現れた。そのジャンプ先に変更する。
        targetJump.replaceTargetLabel(((Jump)commands.get(index)).getTargetLabel());
        corrected = true;
      }
    }
    return corrected;
  }

  /** 使用されていないラベルを削除する。つまり、どこからもジャンプされないラベル */
  protected boolean removeUnusedLabel(ArrayList<Command>input) {
    boolean removed = false;
    Set<Label>useSet = new HashSet<Label>();
    for (Command c: input) {
      if (c instanceof AbstractJump)
        useSet.add(((AbstractJump)c).getTargetLabel());
    }
    for (int i = input.size() - 1; i >= 0; i--) {
      Command c = input.get(i);
      if (!(c instanceof Label)) continue;
      Label label = (Label)c;
      if (!useSet.contains(label)) {
        input.remove(i);
        removed = true;
      }
    }
    return removed;
  }

  /** 使用されていない無条件ジャンプを削除する。
   * 無条件ジャンプの後の無条件ジャンプ */
  protected boolean removeUnusedJump(ArrayList<Command>input) {
    boolean removed = false;
    boolean jumpAfter = false;
    for (int i = input.size() - 1; i >= 0; i--) {
      Command c = input.get(i);
      if (!(c instanceof Jump)) {
        jumpAfter = false;
        continue;
      }
      Jump jump = (Jump)c;
      if (jumpAfter) {
        input.remove(i + 1);
        removed = true;
      } else {
        jumpAfter = true;
      }
    }
    return removed;
  }

  /** すぐ下へのジャンプを削除する。つまり「無条件ジャンプ・ラベル」の
   * ペアで、ジャンプ先がそのラベルになっているもの。 */
  protected boolean removeNeighborJump(ArrayList<Command>input) {
    boolean removed = false;
    Label labelAfter = null;
    for (int i = input.size() - 1; i >= 0; i--) {
      Command c = input.get(i);
      if (c instanceof Label) {
        labelAfter = (Label)c;
        continue;
      }
      if (!(c instanceof Jump)) {
        labelAfter = null;
        continue;
      }
      Jump jump = (Jump)c;
      if (jump.getTargetLabel() == labelAfter) {
        input.remove(i);
        removed = true;
      }
      labelAfter = null;
    }
    return removed;
  }
}
