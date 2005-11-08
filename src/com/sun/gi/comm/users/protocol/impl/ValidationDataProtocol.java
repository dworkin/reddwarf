package com.sun.gi.comm.users.protocol.impl;

import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import java.util.List;
import java.util.ArrayList;

public class ValidationDataProtocol {
	public static final byte CB_TYPE_NAME = 1;

	public static final byte CB_TYPE_PASSWORD = 2;

	public static final byte CB_TYPE_TEXT_INPUT = 3;

	/**
	 * makeRequestData
	 * 
	 * @param callbacks
	 *            Callback[]
	 */
	public static void makeRequestData(ByteBuffer requestData,
			Callback[] currentCallbacks) throws UnsupportedCallbackException {
		requestData.putInt(currentCallbacks.length);
		for (int i = 0; i < currentCallbacks.length; i++) {
			Callback cb = currentCallbacks[i];
			if (cb instanceof NameCallback) {
				requestData.put(CB_TYPE_NAME);
				String prompttext = ((NameCallback) cb).getPrompt();
				if (prompttext == null){
					prompttext = "";
				}
				byte[] prompt = prompttext.getBytes();
				requestData.putInt(prompt.length);
				requestData.put(prompt);
				String defaultName = ((NameCallback) cb).getDefaultName();
				if (defaultName == null){
					defaultName = "";
				}
				byte[] defaulttext = defaultName.getBytes();
				requestData.putInt(defaulttext.length);
				requestData.put(defaulttext);
				String resultText =((NameCallback) cb).getName();
				if (resultText == null){
					resultText = "";
				}
				byte[] result = resultText.getBytes();
				requestData.putInt(result.length);
				requestData.put(result);
			} else if (cb instanceof PasswordCallback) {
				requestData.put(CB_TYPE_PASSWORD);
				String prompttext = ((PasswordCallback) cb).getPrompt();
				if (prompttext == null){
					prompttext="";
				}
				byte[] prompt = prompttext.getBytes();
				requestData.putInt(prompt.length);
				requestData.put(prompt);
				requestData.put((byte) (((PasswordCallback) cb).isEchoOn() ? 1
						: 0));
				String resulttext="";
				char[] resultchars = ((PasswordCallback) cb).getPassword();
				if (resultchars!=null){				
					resulttext = new String(resultchars);
				}
				byte[] result = resulttext.getBytes();
				requestData.putInt(result.length);
				requestData.put(result);
			} else if (cb instanceof TextInputCallback) {
				requestData.put(CB_TYPE_TEXT_INPUT);
				String prompttext = ((TextInputCallback) cb).getPrompt();
				if (prompttext == null) {
					prompttext = "";
				}
				byte[] prompt = prompttext.getBytes();
				requestData.putInt(prompt.length);
				requestData.put(prompt);
				String defaultstr = ((TextInputCallback) cb).getDefaultText();
				if (defaultstr==null){
					defaultstr="";
				}
				byte[] defaultText = defaultstr.getBytes();
				requestData.putInt(defaultText.length);
				requestData.put(defaultText);
				String resultstr = ((TextInputCallback) cb).getText();
				if (resultstr == null){
					resultstr = "";
				}
				byte[] result = resultstr.getBytes();
				requestData.putInt(result.length);
				requestData.put(result);
			} else {
				throw new UnsupportedCallbackException(cb);
			}
		}
	}

	/**
	 * 
	 * /** unpackRequestData
	 * 
	 * @param buff
	 *            ByteBuffer
	 */
	public static Callback[] unpackRequestData(ByteBuffer requestData) {
		ByteBuffer buff = requestData;
		int callbackCount = buff.getInt();
		List<Callback> callbackList = new ArrayList<Callback>();
		for (int i = 0; i < callbackCount; i++) {
			byte cbType = buff.get();
			switch (cbType) {
			case CB_TYPE_NAME:
				int strlen = buff.getInt();
				byte[] strbytes = new byte[strlen];
				buff.get(strbytes);
				String prompt = new String(strbytes);
				strlen = buff.getInt();
				strbytes = new byte[strlen];
				buff.get(strbytes);
				String defaulttext = new String(strbytes);
				strlen = buff.getInt();
				strbytes = new byte[strlen];
				buff.get(strbytes);
				String name = new String(strbytes);
				NameCallback namecb;
				if (prompt.length()==0){
					System.out.println("Error: Received illegal name callback with no prompt.");
					prompt = "<UNSPECIFIED>";
				}
				if (defaulttext.length()==0){
					namecb = new NameCallback(prompt);
				} else {
					namecb = new NameCallback(prompt, defaulttext);
				}
				namecb.setName(name);
				callbackList.add(namecb);
				break;
			case CB_TYPE_PASSWORD:
				strlen = buff.getInt();
				strbytes = new byte[strlen];
				buff.get(strbytes);
				prompt = new String(strbytes);
				boolean echoOn = (buff.get()==1)?true:false;
				strlen = buff.getInt();
				strbytes = new byte[strlen];
				buff.get(strbytes);
				String password = new String(strbytes);
				if (prompt.length()==0){
					System.out.println("Error: Received illegal name callback with no prompt.");
					prompt = "<UNSPECIFIED>";
				}
				PasswordCallback pcb = new PasswordCallback(prompt, false);
				pcb.setPassword(password.toCharArray());
				callbackList.add(pcb);
				break;
			case CB_TYPE_TEXT_INPUT:
				strlen = buff.getInt();
				strbytes = new byte[strlen];
				buff.get(strbytes);
				prompt = new String(strbytes);
				strlen = buff.getInt();
				strbytes = new byte[strlen];
				buff.get(strbytes);
				defaulttext = new String(strbytes);
				strlen = buff.getInt();
				strbytes = new byte[strlen];
				buff.get(strbytes);
				String response = new String(strbytes);
				if (prompt.length()==0){
					System.out.println("Error: Received illegal name callback with no prompt.");
					prompt = "<UNSPECIFIED>";
				}
				TextInputCallback tcb;
				if (defaulttext.length()==0){
					tcb = new TextInputCallback(prompt);
				} else {	
					tcb = new TextInputCallback(prompt,
							defaulttext);
				}
				tcb.setText(response);
				callbackList.add(tcb);
				break;
			default:
				System.out.println("Error: Illegal login callback type: "
						+ cbType);
				return null;
			}

		}
		Callback[] cbArray = new Callback[callbackList.size()];
		callbackList.toArray(cbArray);
		return cbArray;
	}

}
