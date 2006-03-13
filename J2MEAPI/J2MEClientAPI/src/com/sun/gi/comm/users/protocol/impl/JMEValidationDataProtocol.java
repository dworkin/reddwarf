/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.comm.users.protocol.impl;

import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.Callback;
import com.sun.gi.utils.jme.NameCallback;
import com.sun.gi.utils.jme.PasswordCallback;
import com.sun.gi.utils.jme.TextInputCallback;
import com.sun.gi.utils.jme.UnsupportedCallbackException;
import java.util.Vector;

public class JMEValidationDataProtocol {
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
		Vector callbackList = new Vector();
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
				callbackList.addElement(namecb);
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
				callbackList.addElement(pcb);
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
				callbackList.addElement(tcb);
				break;
			default:
				System.out.println("Error: Illegal login callback type: "
						+ cbType);
				return null;
			}

		}
		Callback[] cbArray = new Callback[callbackList.size()];
                callbackList.copyInto(cbArray);
		//callbackList.toArray(cbArray);
		return cbArray;
	}

}
