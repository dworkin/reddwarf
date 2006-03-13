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


/*
 * ChatPanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 5:52:06 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.apps.hack.client.ChatListener;
import com.sun.gi.apps.hack.client.ChatManager;

import java.awt.BorderLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;


/**
 * This implements a simple chat front-end with a text field for sending
 * messages and a larger text area for displaying messages. It is driven by
 * a listener/manager model, so you can hook this panel up to any backing
 * system you like.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class ChatPanel extends JPanel implements ActionListener, ChatListener
{

    // the display area
    private JTextArea textArea;

    // the entry field
    private JTextField textField;

    // the manager that we notify with chat messages
    private ChatManager chatManager;

    // the panel that we re-focus when we done typing
    private JComponent focusPanel;

    // the mapping from uid to name
    private Map<UserID,String> uidMap;

    /**
     * Creates a <code>Chatmanager</code>.
     *
     * @param chatmanager the manager that recieves chat messages
     * @param focusPanel the panel that shares focus with us
     */
    public ChatPanel(ChatManager chatManager, JComponent focusPanel) {
        super(new BorderLayout(4, 4));

        uidMap = new HashMap<UserID,String>();

        // track the manager, and add ourselves as a listener
        this.chatManager = chatManager;
        chatManager.addChatListener(this);

        this.focusPanel = focusPanel;

        // create a 7-column display area
        textArea = new JTextArea();
        textArea.setRows(7);
        textArea.setLineWrap(true);
        textArea.setEditable(false);

        // create the entry field, and capture return key-presses
        textField = new JTextField();
        textField.addActionListener(this);

        add(textArea, BorderLayout.CENTER);
        add(textField, BorderLayout.SOUTH);
    }

    /**
     * Clears all the current messages in the display area.
     */
    public void clearMessages() {
        textArea.setText("");
    }

    /**
     * Called when return is typed from the entry field.
     *
     * @param e details about the action
     */
    public void actionPerformed(ActionEvent e) {
        // get the current text and send it off, and clear the entry field
        chatManager.sendMessage(textField.getText());
        textField.setText("");

        // return focus to the game panel
        focusPanel.requestFocusInWindow();
    }

    /**
     *
     */
    public void playerJoined(UserID uid) {
        if (uidMap.containsKey(uid))
            textArea.append(uidMap.get(uid) + ": *joined*\n");
    }

    /**
     *
     */
    public void playerLeft(UserID uid) {
        if (uidMap.containsKey(uid))
            textArea.append(uidMap.get(uid) + ": *left*\n");
    }

    /**
     * Callback that is invoked when a message arrives.
     *
     * @param sender the name of the sender
     * @param message the message itself
     */
    public void messageArrived(UserID sender, String message) {
        if (uidMap.containsKey(sender))
            textArea.append(uidMap.get(sender) + ": " + message + "\n");
    }

    /**
     *
     */
    public void addUidMappings(Map<UserID,String> uidMap) {
        this.uidMap.putAll(uidMap);
    }

}
