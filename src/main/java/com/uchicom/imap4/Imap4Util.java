// (c) 2016 uchicom
package com.uchicom.imap4;

import java.io.PrintStream;


public class Imap4Util {

	/**
	 * コマンドがUSERかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isUser(String cmd) {
		return cmd.matches(Constants.REG_EXP_USER);
	}


	/**
	 * コマンドがPASSかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isPass(String cmd) {
		return cmd.matches(Constants.REG_EXP_PASS);
	}

	/**
	 * コマンドがSTATかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isStat(String cmd) {
		return cmd.matches(Constants.REG_EXP_STAT);
	}

	/**
	 * コマンドがLISTかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isList(String cmd) {
		return cmd.matches(Constants.REG_EXP_LIST);
	}

	/**
	 * コマンドがLIST 番号かどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isListNum(String cmd) {
		return cmd.matches(Constants.REG_EXP_LIST_NUM);
	}

	/**
	 * コマンドがRETRかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isRetr(String cmd) {
		return cmd.matches(Constants.REG_EXP_RETR);
	}

	/**
	 * コマンドがRETR 番号かどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isRetrNum(String cmd) {
		return cmd.matches(Constants.REG_EXP_RETR_NUM);
	}

	/**
	 * コマンドがDELE 番号かどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isDeleNum(String cmd) {
		return cmd.matches(Constants.REG_EXP_DELE_NUM);
	}

	/**
	 * コマンドがRsetかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isRset(String cmd) {
		return cmd.matches(Constants.REG_EXP_RSET);
	}

	/**
	 * コマンドがQuitかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isQuit(String cmd) {
		return cmd.matches(Constants.REG_EXP_QUIT);
	}

	/**
	 * コマンドがTOP 番号 番号かどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isTopNumNum(String cmd) {
		return cmd.matches(Constants.REG_EXP_TOP_NUM_NUM);
	}

	/**
	 * コマンドがUIDLかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isUidl(String cmd) {
		return cmd.matches(Constants.REG_EXP_UIDL);
	}

	/**
	 * コマンドがUIDL 番号かどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isUidlNum(String cmd) {
		return cmd.matches(Constants.REG_EXP_UIDL_NUM);
	}

	/**
	 * コマンドがAPOP ユーザー名 ダイジェストかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isApopNameDigest(String cmd) {
		return cmd.matches(Constants.REG_EXP_APOP_NAME_DIGEST);
	}

	/**
	 * コマンドがNOOPかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isNoop(String cmd) {
		return cmd.matches(Constants.REG_EXP_NOOP);
	}

	/**
	 * コマンドがCAPAかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isCapa(String cmd) {
		return cmd.matches(Constants.REG_EXP_CAPA);
	}

	/**
	 * コマンドがSTLSかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isStls(String cmd) {
		return cmd.matches(Constants.REG_EXP_STLS);
	}

	/**
	 * ステータス行を出力する.
	 * @param ps
	 * @param strings
	 */
	public static void recieveLine(PrintStream ps, String... strings) {
		for (String string : strings) {
			ps.print(string);
		}
		ps.print(Constants.RECV_LINE_END);
		ps.flush();
	}

	public static void log(String string) {
		if (Constants.DEBUG) {
			System.out.println(string);
		}
	}
}
