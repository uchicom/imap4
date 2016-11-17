// (c) 2016 uchicom
package com.uchicom.imap4;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.uchicom.server.Parameter;
import com.uchicom.server.ServerProcess;

public class Imap4Process implements ServerProcess {

	/** ファイルとUIDLで使用する日時フォーマット */
	private final SimpleDateFormat format = new SimpleDateFormat(
			Constants.DATE_TIME_MILI_FORMAT);
	private Parameter parameter;
	private Socket socket;

	protected FileComparator comparator = new FileComparator();

	/** 最終処理時刻 */
	private long lastTime = System.currentTimeMillis();

	/**
	 * コンストラクタ.
	 *
	 * @param parameter
	 * @param socket
	 * @throws IOException
	 */
	public Imap4Process(Parameter parameter, Socket socket) {
		this.parameter = parameter;
		this.socket = socket;
	}

	/**
	 * pop3処理.
	 */
	public void execute() {
		Imap4Util.log(format.format(new Date()) + ":"
				+ String.valueOf(socket.getRemoteSocketAddress()));
		// 0.はプロセスごとに変える番号だけど、とくに複数プロセスを持っていないので。
		String timestamp = "<" + Thread.currentThread().getId() + "."
				+ System.currentTimeMillis() + "@" + parameter.get("hostName")
				+ ">";
		BufferedReader br = null;
		PrintStream ps = null;
		try {
			br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			ps = new PrintStream(socket.getOutputStream());
			// 1接続に対する受付開始
			Imap4Util.recieveLine(ps, Constants.RECV_OK, " ", timestamp);
			// 以下はログイン中のみ有効な変数
			String line = br.readLine();
			// ユーザーコマンドでユーザーが設定されたかどうかのフラグ
			boolean bUser = false;
			// 認証が許可されたかどうかのフラグ
			boolean bPass = false;
			String user = null;
			String pass = null;
			File userBox = null;
			// メールbox内にあるメールリスト(PASSコマンド時に認証が許可されると設定される)
			List<File> mailList = null;
			// DELEコマンド時に指定したメールが格納される(PASSコマンド時に認証が許可されると設定される)
			List<File> delList = null;
			while (line != null) {
				if (Imap4Util.isUser(line)) {
					bUser = true;
					user = line.split(" ")[1];
					Imap4Util.recieveLine(ps, Constants.RECV_OK);
				} else if (Imap4Util.isPass(line)) {
					if (bUser && !bPass) {
						pass = line.split(" ")[1];
						// ユーザーチェック
						boolean existUser = false;
						for (File box : parameter.getFile("dir").listFiles()) {
							if (box.isDirectory()) {
								if (user.equals(box.getName())) {
									userBox = box;
									File[] mails = userBox
											.listFiles(new FilenameFilter() {

												@Override
												public boolean accept(File dir,
														String name) {
													File file = new File(dir,
															name);
													if (file.isFile()
															&& !file.isHidden()
															&& file.canRead()
															&& !Constants.PASSWORD_FILE_NAME
																	.equals(name)) {
														return true;
													}
													return false;
												}

											});

									mailList = Arrays.asList(mails);
									Collections.sort(mailList, comparator);
									delList = new ArrayList<File>();

									existUser = true;
								}
							}
						}
						if (existUser) {
							// パスワードチェック
							if (!"".equals(pass)) {
								File passwordFile = new File(userBox,
										Constants.PASSWORD_FILE_NAME);
								if (passwordFile.exists()
										&& passwordFile.isFile()) {
									BufferedReader passReader = new BufferedReader(
											new InputStreamReader(
													new FileInputStream(
															passwordFile)));
									String password = passReader.readLine();
									while ("".equals(password)) {
										password = passReader.readLine();
									}
									passReader.close();
									if (pass.equals(password)) {

										Imap4Util.recieveLine(ps,
												Constants.RECV_OK);
										bPass = true;
									} else {
										// パスワード不一致エラー
										Imap4Util.recieveLine(ps,
												Constants.RECV_NG);
									}
								} else {
									// パスワードファイルなしエラー
									Imap4Util.recieveLine(ps, Constants.RECV_NG);
								}
							} else {
								// パスワード入力なしエラー
								Imap4Util.recieveLine(ps, Constants.RECV_NG);
							}
						} else {
							// ユーザー存在しないエラー
							Imap4Util.recieveLine(ps, Constants.RECV_NG);
						}
					} else {
						// ユーザー名未入力エラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (Imap4Util.isStat(line)) {
					if (bPass) {
						// 簡易一覧表示
						long fileLength = 0;
						int fileCnt = 0;
						for (File child : mailList) {
							if (!delList.contains(child)) {
								fileLength += child.length();
								fileCnt++;
							}
						}
						Imap4Util.recieveLine(ps, Constants.RECV_OK, " ",
								String.valueOf(fileCnt), " ",
								String.valueOf(fileLength));
					} else {
						// 認証なしエラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (Imap4Util.isList(line)) {
					if (bPass) {
						// リスト表示
						Imap4Util.recieveLine(ps, Constants.RECV_OK);
						for (int i = 0; i < mailList.size(); i++) {
							File child = mailList.get(i);
							if (!delList.contains(child)) {

								Imap4Util.recieveLine(ps, String.valueOf(i + 1),
										" ", String.valueOf(child.length()));
							}
						}
						Imap4Util.recieveLine(ps, Constants.RECV_DATA);
					} else {
						// 認証なしエラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
					ps.flush();
				} else if (Imap4Util.isListNum(line)) {
					if (bPass) {
						// 指定番号のリスト表示
						String[] heads = line.split(" ");
						int index = Integer.parseInt(heads[1]) - 1;
						if (0 <= index && index < mailList.size()) {
							File child = mailList.get(index);
							if (!delList.contains(child)) {
								Imap4Util.recieveLine(ps, Constants.RECV_OK,
										" ", line.substring(5), " ",
										String.valueOf(child.length()));
							} else {
								Imap4Util.recieveLine(ps, Constants.RECV_NG);
							}
						} else {
							// index範囲外
							Imap4Util.recieveLine(ps, Constants.RECV_NG);
						}
					} else {
						// 認証なしエラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (Imap4Util.isRetr(line)) {
					if (bPass) {
						Imap4Util.recieveLine(ps, Constants.RECV_OK);
						for (File child : mailList) {
							if (!delList.contains(child)) {
								BufferedReader fileReader = new BufferedReader(
										new InputStreamReader(
												new FileInputStream(child)));
								String readLine = fileReader.readLine();
								while (readLine != null) {
									if (readLine.length() > 0 && readLine.charAt(0) == '.') {
										ps.write((byte)'.');
									}
									Imap4Util.recieveLine(ps, readLine);
									readLine = fileReader.readLine();
								}
								fileReader.close();
							}
						}
						Imap4Util.recieveLine(ps, Constants.RECV_DATA);
					} else {
						// エラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (Imap4Util.isRetrNum(line)) {
					if (bPass) {
						String[] heads = line.split(" ");
						int index = Integer.parseInt(heads[1]) - 1;
						if (0 <= index && index < mailList.size()) {
							File child = mailList.get(index);
							if (!delList.contains(child)) {
								Imap4Util.recieveLine(ps, Constants.RECV_OK, " ", String.valueOf(child.length()));
								BufferedReader fileReader = new BufferedReader(
										new InputStreamReader(
												new FileInputStream(child)));
								String readLine = fileReader.readLine();
								while (readLine != null) {
									if (readLine.length() > 0 && readLine.charAt(0) == '.') {
										ps.write((byte)'.');
									}
									Imap4Util.recieveLine(ps, readLine);
									readLine = fileReader.readLine();
								}
								Imap4Util.recieveLine(ps, Constants.RECV_DATA);
								fileReader.close();
							} else {
								Imap4Util.recieveLine(ps, Constants.RECV_NG);
							}
						} else {
							// index範囲外
							Imap4Util.recieveLine(ps, Constants.RECV_NG);
						}
					} else {
						// エラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
					ps.flush();
				} else if (Imap4Util.isDeleNum(line)) {
					if (bPass) {
						// 削除処理
						String[] heads = line.split(" ");
						int index = Integer.parseInt(heads[1]) - 1;
						if (0 <= index && index < mailList.size()) {
							File child = mailList.get(index);
							delList.add(child);
							Imap4Util.recieveLine(ps, Constants.RECV_OK);
						} else {
							// index範囲外
							Imap4Util.recieveLine(ps, Constants.RECV_NG);
						}
					} else {
						// エラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (Imap4Util.isRset(line)) {
					// リセット
					if (bPass) {
						// 消去マークを無くす
						delList.clear();
						Imap4Util.recieveLine(ps, Constants.RECV_OK);
					} else {
						// エラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (Imap4Util.isQuit(line)) {
					if (delList != null) {
						// 消去マークの入ったファイルを削除する
						for (File delFile : delList) {
							delFile.delete();
						}
					}
					Imap4Util.recieveLine(ps, Constants.RECV_OK);
					// 削除失敗時は-ERRを返すべきだけどまだやってない。
					break;
				} else if (Imap4Util.isTopNumNum(line)) {
					if (bPass) {
						// TRANSACTION 状態でのみ許可される
						String[] heads = line.split(" ");
						int index = Integer.parseInt(heads[1]) - 1;
						if (0 <= index && index < mailList.size()) {
							File child = mailList.get(index);
							if (!delList.contains(child)) {
								Imap4Util.recieveLine(ps, Constants.RECV_OK);
								BufferedReader fileReader = new BufferedReader(
										new InputStreamReader(
												new FileInputStream(child)));
								String readLine = fileReader.readLine();
								int maxRow = Integer.parseInt(heads[2]);
								int row = 0;
								boolean messageHead = true;
								while (readLine != null
										&& (messageHead || row <= maxRow)) {
									Imap4Util.recieveLine(ps, readLine);
									readLine = fileReader.readLine();
									if (!messageHead) {
										row++;
									}
									if ("".equals(readLine)) {
										messageHead = false;
									}
								}
								Imap4Util.recieveLine(ps, Constants.RECV_DATA);
								fileReader.close();
							} else {
								Imap4Util.recieveLine(ps, Constants.RECV_NG);
							}
						} else {
							Imap4Util.recieveLine(ps, Constants.RECV_NG);
						}
					} else {
						// 認証なしエラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (Imap4Util.isUidl(line)) {
					if (bPass) {
						// TRANSACTION 状態でのみ許可される
						Imap4Util.recieveLine(ps, Constants.RECV_OK);
						for (int i = 0; i < mailList.size(); i++) {
							File child = mailList.get(i);
							if (!delList.contains(child)) {
								ps.print(i + 1);
								ps.print(' ');
								String name = child.getName();
								int lastIndex = name.lastIndexOf('~');
								if (lastIndex < 0) {
									if (name.length() > 70) {
										lastIndex = name.length() - 70;
									} else {
										ps.print(name);
									}
								} else {
									ps.print(name.substring(lastIndex));
								}
								ps.print(Constants.RECV_LINE_END);
							}
						}
						Imap4Util.recieveLine(ps, Constants.RECV_DATA);
					} else {
						// 認証なしエラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
					ps.flush();
				} else if (Imap4Util.isUidlNum(line)) {
					if (bPass) {
						// TRANSACTION 状態でのみ許可される
						String[] heads = line.split(" ");
						int index = Integer.parseInt(heads[1]) - 1;
						if (0 <= index && index < mailList.size()) {
							File child = mailList.get(index);
							if (!delList.contains(child)) {
								ps.print(Constants.RECV_OK);
								ps.print(' ');
								ps.print(heads[1]);
								ps.print(' ');
								String name = child.getName();
								int lastIndex = name.lastIndexOf('~');
								if (lastIndex < 0) {
									if (name.length() > 70) {
										lastIndex = name.length() - 70;
									} else {
										ps.print(name);
									}
								} else {
									ps.print(name.substring(lastIndex));
								}
								ps.print(Constants.RECV_LINE_END);
							} else {
								Imap4Util.recieveLine(ps, Constants.RECV_NG);
							}
						} else {
							// index範囲外
							Imap4Util.recieveLine(ps, Constants.RECV_NG);
						}
					} else {
						// 認証なしエラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
					ps.flush();
				} else if (Imap4Util.isApopNameDigest(line)) {
					if (!bPass) {
						// 未実装のためエラーとする
						String[] heads = line.split(" ");
						user = heads[1];
						String digest = heads[2];
						// ユーザーチェック
						boolean existUser = false;
						for (File box : parameter.getFile("dir").listFiles()) {
							if (box.isDirectory()) {
								if (user.equals(box.getName())) {
									userBox = box;
									File[] mails = userBox
											.listFiles(new FilenameFilter() {
												@Override
												public boolean accept(File dir,
														String name) {
													File file = new File(dir,
															name);
													if (file.isFile()
															&& !file.isHidden()
															&& !Constants.PASSWORD_FILE_NAME
																	.equals(name)) {
														return true;
													}
													return false;
												}
											});
									mailList = Arrays.asList(mails);
									Collections.sort(mailList, comparator);
									delList = new ArrayList<File>();
									existUser = true;
								}
							}
						}
						if (existUser) {
							// パスワードチェック
							File passwordFile = new File(userBox,
									Constants.PASSWORD_FILE_NAME);
							if (passwordFile.exists() && passwordFile.isFile()) {
								BufferedReader passReader = new BufferedReader(
										new InputStreamReader(
												new FileInputStream(
														passwordFile)));
								String password = passReader.readLine();
								while ("".equals(password)) {
									password = passReader.readLine();
								}
								passReader.close();
								// ダイジェストとタイムスタンプを元にダイジェストを作成
								MessageDigest md = MessageDigest
										.getInstance("MD5");
								md.update((timestamp + password).getBytes());
								byte[] passBytes = md.digest();
								StringBuffer strBuff = new StringBuffer(32);
								for (int i = 0; i < passBytes.length; i++) {
									int d = passBytes[i] & 0xFF;
									if (d < 0x10) {
										strBuff.append("0");
									}
									strBuff.append(Integer.toHexString(d));
								}
								if (digest.equals(strBuff.toString())) {
									Imap4Util.recieveLine(ps, Constants.RECV_OK);
									bPass = true;
								} else {
									// パスワード不一致エラー
									Imap4Util.recieveLine(ps, Constants.RECV_NG);
								}
							} else {
								// パスワードファイルなしエラー
								Imap4Util.recieveLine(ps, Constants.RECV_NG);
							}
						} else {
							// ユーザー存在しないエラー
							Imap4Util.recieveLine(ps, Constants.RECV_NG);
						}
					} else {
						// パスワード認証後に再度パスワード認証はエラー
						Imap4Util.recieveLine(ps, Constants.RECV_NG);
					}
				} else if (line.length() == 0 || Imap4Util.isNoop(line)) {
					// 何もしない
				} else {
					//コマンドエラー
					Imap4Util.recieveLine(ps, Constants.RECV_NG_CMD_NOT_FOUND);
				}
				lastTime = System.currentTimeMillis();
				line = br.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			if (ps != null) {
				try {
					ps.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (br != null) {
				try {
					br.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			synchronized (socket) {
				if (socket != null) {
					try {
						socket.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
					socket = null;
				}
			}
		}
	}

	/**
	 * 最終処理時刻を取得します.
	 *
	 * @return
	 */
	public long getLastTime() {
		return lastTime;
	}

	/**
	 * 強制終了.
	 */
	public void forceClose() {
		//		if (rejectMap.containsKey(senderAddress)) {
		//			rejectMap.put(senderAddress, rejectMap.get(senderAddress) + 1);
		//		} else {
		//			rejectMap.put(senderAddress, 1);
		//		}
		System.out.println("forceClose!");
		if (socket != null && socket.isConnected()) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			socket = null;
		}
	}
}
