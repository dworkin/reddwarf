/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.comm.users.protocol.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

public class ValidationDataProtocol {

    private static Logger log = Logger.getLogger("com.sun.gi.comm.users");

    public static final byte CB_TYPE_NAME       = 1;
    public static final byte CB_TYPE_PASSWORD   = 2;
    public static final byte CB_TYPE_TEXT_INPUT = 3;

    /**
     * makeRequestData
     * 
     * @param requestData
     * @param currentCallbacks authentication credentials
     */
    public static void makeRequestData(ByteBuffer requestData,
            Callback[] currentCallbacks) throws UnsupportedCallbackException {
        requestData.putInt(currentCallbacks.length);
        for (Callback cb : currentCallbacks) {
            if (cb instanceof NameCallback) {
                requestData.put(CB_TYPE_NAME);
                String prompttext = ((NameCallback) cb).getPrompt();
                if (prompttext == null) {
                    prompttext = "";
                }
                byte[] prompt = prompttext.getBytes();
                requestData.putInt(prompt.length);
                requestData.put(prompt);
                String defaultName = ((NameCallback) cb).getDefaultName();
                if (defaultName == null) {
                    defaultName = "";
                }
                byte[] defaulttext = defaultName.getBytes();
                requestData.putInt(defaulttext.length);
                requestData.put(defaulttext);
                String resultText = ((NameCallback) cb).getName();
                if (resultText == null) {
                    resultText = "";
                }
                byte[] result = resultText.getBytes();
                requestData.putInt(result.length);
                requestData.put(result);
            } else if (cb instanceof PasswordCallback) {
                requestData.put(CB_TYPE_PASSWORD);
                String prompttext = ((PasswordCallback) cb).getPrompt();
                if (prompttext == null) {
                    prompttext = "";
                }
                byte[] prompt = prompttext.getBytes();
                requestData.putInt(prompt.length);
                requestData.put(prompt);
                requestData.put((byte) (((PasswordCallback) cb).isEchoOn() ? 1
                        : 0));
                String resulttext = "";
                char[] resultchars = ((PasswordCallback) cb).getPassword();
                if (resultchars != null) {
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
                if (defaultstr == null) {
                    defaultstr = "";
                }
                byte[] defaultText = defaultstr.getBytes();
                requestData.putInt(defaultText.length);
                requestData.put(defaultText);
                String resultstr = ((TextInputCallback) cb).getText();
                if (resultstr == null) {
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
     * @param requestData ByteBuffer
     */
    public static Callback[] unpackRequestData(ByteBuffer requestData) {
        ByteBuffer buff = requestData;
        int callbackCount = buff.getInt();
        List<Callback> callbackList = new ArrayList<Callback>(callbackCount);

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
                    if (prompt.length() == 0) {
                        log.warning("Name callback has no prompt.");
                        prompt = "<UNSPECIFIED>";
                    }
                    if (defaulttext.length() == 0) {
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
                    boolean echoOn = (buff.get() == 1) ? true : false;
                    strlen = buff.getInt();
                    strbytes = new byte[strlen];
                    buff.get(strbytes);
                    String password = new String(strbytes);
                    if (prompt.length() == 0) {
                        log.warning("Password callback has no prompt.");
                        prompt = "<UNSPECIFIED>";
                    }
                    PasswordCallback pcb = new PasswordCallback(prompt, echoOn);
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
                    if (prompt.length() == 0) {
                        log.warning("Text-input callback has no prompt.");
                        prompt = "<UNSPECIFIED>";
                    }
                    TextInputCallback tcb;
                    if (defaulttext.length() == 0) {
                        tcb = new TextInputCallback(prompt);
                    } else {
                        tcb = new TextInputCallback(prompt, defaulttext);
                    }
                    tcb.setText(response);
                    callbackList.add(tcb);
                    break;

                default:
                    log.warning("Unknown login callback type: " + cbType);
                    return null;
            }
        }
        Callback[] cbArray = new Callback[callbackList.size()];
        callbackList.toArray(cbArray);
        return cbArray;
    }
}
