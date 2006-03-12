
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
