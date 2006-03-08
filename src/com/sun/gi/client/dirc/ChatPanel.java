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
 * 
 * Created on: Sat Feb 25, 2006 5:52:06 PM
 */

package com.sun.gi.client.dirc;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * A simple chat front-end with a text field for sending messages and a
 * larger text area for displaying messages. It is driven by a
 * listener/manager model, so you can hook this panel up to any backing
 * system you like.
 * 
 * @since 1.0
 * @author Seth Proctor
 */
public class ChatPanel extends JPanel implements ActionListener, ChatListener {

    private static final long serialVersionUID = 1L;

    // the display area
    private JTextArea outputArea;

    // the entry field
    private JTextField inputField;

    // the manager that we notify with chat messages
    private ChatManager chatManager;

    /**
     * Creates a <code>Chatmanager</code>.
     * 
     * @param chatManager the manager that recieves chat messages
     */
    public ChatPanel(ChatManager chatManager) {
        super(new BorderLayout(4, 4));

        // track the manager, and add ourselves as a listener
        this.chatManager = chatManager;

        // create a display area
        outputArea = new JTextArea();
        outputArea.setColumns(40);
        outputArea.setRows(40);
        outputArea.setLineWrap(true);

        // create the entry field, and capture return key-presses
        inputField = new JTextField();
        inputField.addActionListener(this);

        add(outputArea, BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);
    }

    /**
     * Clears all the current messages in the display area.
     */
    public void clearMessages() {
        outputArea.setText("");
    }

    /**
     * Called when return is typed from the entry field.
     * 
     * @param e details about the action
     */
    public void actionPerformed(ActionEvent e) {
        // get the current text and send it off, and clear the entry
        // field
        chatManager.handleChatInput(inputField.getText());
        inputField.setText("");
    }

    /**
     * Callback that is invoked when a message arrives.
     * 
     * @param sender the name of the sender. null means it's the server.
     * @param channel the channel the message came in on, if any
     * @param message the message itself
     */
    public void messageArrived(String sender, String channel, String message) {
        outputArea.append("[" + sender + "@" + channel + "]: " + message + "\n");
    }

    public void info(String message) {
        outputArea.append("* " + message + "\n");
    }
}
