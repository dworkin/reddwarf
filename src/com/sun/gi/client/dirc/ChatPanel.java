/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 *
 * ChatPanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 5:52:06 PM
 * Desc: 
 *
 */

package com.sun.gi.client.dirc;

import java.awt.BorderLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
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
public class ChatPanel extends JPanel implements ActionListener, ChatListener
{

    // the display area
    private JTextArea outputArea;

    // the channel area
    private JTextArea channelArea;

    // the entry field
    private JTextField inputField;

    // the manager that we notify with chat messages
    private ChatManager chatManager;

    /**
     * Creates a <code>Chatmanager</code>.
     *
     * @param chatmanager the manager that recieves chat messages
     * @param focusPanel the panel that shares focus with us
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

        // create a channel list
        channelArea = new JTextArea();
        channelArea.setColumns(10);
        channelArea.setRows(42);
        channelArea.setLineWrap(false);
        channelArea.setEditable(false);

        // create the entry field, and capture return key-presses
        inputField = new JTextField();
        inputField.addActionListener(this);

        add(outputArea, BorderLayout.CENTER);
        add(channelArea, BorderLayout.EAST);
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
        // get the current text and send it off, and clear the entry field
        chatManager.handleChatInput(inputField.getText());
        inputField.setText("");
    }

    /**
     * Callback that is invoked when a message arrives.
     *
     * @param sender  the name of the sender.  null means it's the server.
     * @param channel the channel the message came in on, if any
     * @param message the message itself
     */
    public void messageArrived(String sender, String channel, String message) {
        outputArea.append(
	    "[" + sender + "@" + channel + "]: " + message + "\n");
    }

    public void info(String message) {
        outputArea.append("* " + message + "\n");
    }
}
