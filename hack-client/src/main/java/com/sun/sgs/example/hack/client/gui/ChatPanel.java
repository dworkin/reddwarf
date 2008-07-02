/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.ChatListener;
import com.sun.sgs.example.hack.client.ChatManager;

import java.awt.BorderLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;


/**
 * This implements a simple chat front-end with a text field for sending
 * messages and a larger text area for displaying messages. It is driven by
 * a listener/manager model, so you can hook this panel up to any backing
 * system you like.
 */
public class ChatPanel extends JPanel implements ActionListener, ChatListener
{

    private static final long serialVersionUID = 1;

    // the display area
    private JTextArea textArea;

    // the entry field
    private JTextField textField;

    // the manager that we notify with chat messages
    private ChatManager chatManager;

    // the panel that we re-focus when we done typing
    private JComponent focusPanel;

    // the mapping from uid to name
    private Map<BigInteger,String> uidMap;

    // the client's current session id
    private BigInteger currentSession;

    /**
     * Creates a <code>ChatManager</code>.
     *
     * @param chatManager the manager that receives chat messages
     * @param focusPanel the panel that shares focus with us
     */
    public ChatPanel(ChatManager chatManager, JComponent focusPanel) {
        super(new BorderLayout(4, 4));

        uidMap = new HashMap<BigInteger,String>();

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

        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(textField, BorderLayout.SOUTH);
    }

    /**
     * Sets the session id for the client.
     */
    public void setSessionId(BigInteger session) {
        uidMap.remove(currentSession);
        uidMap.put(session, "[You]");
        currentSession = session;
    }

    /**
     * Returns the current session id for the client.
     */
    public BigInteger getSessionId() {
	return currentSession;
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
        String message = textField.getText();

        // get the current text and send it off, and clear the entry field
        chatManager.sendMessage(message);
        messageArrived(message);
        textField.setText("");

        // return focus to the game panel
        focusPanel.requestFocusInWindow();
    }

    /**
     * Called when a player joins the chat.
     *
     * @param uid the identifier of the player that joined
     */
    public void playerJoined(BigInteger uid) {
        if (uidMap.containsKey(uid))
            textArea.append(uidMap.get(uid) + ": *joined*\n");
    }

    /**
     * Called when a player leaves the chat.
     *
     * @param uid the identifier of the player that left
     */
    public void playerLeft(BigInteger uid) {
        if (uidMap.containsKey(uid))
            textArea.append(uidMap.get(uid) + ": *left*\n");
    }

    /**
     * Callback that is invoked when a message arrives.
     *
     * @param message the message itself
     */
    public void messageArrived(String message) {
	textArea.append(message + "\n");
    }

    /**
     * Called when there is new information about the mapping from user
     * identifiers to user names.
     *
     * @param uidMap the mapping from identifiers to names
     */
    public void addUidMappings(Map<BigInteger,String> uidMap) {
        this.uidMap.putAll(uidMap);
    }

}
