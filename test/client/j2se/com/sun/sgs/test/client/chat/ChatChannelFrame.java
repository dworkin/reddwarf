package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;

/**
 * <p>
 * ChatChannelFrame presents a GUI so that a user can interact with
 * a channel. The users connected to the channel are displayed in a list
 * on the right side. Messages can be sent on the channel via an input
 * area on the left side.
 * </p>
 */
public class ChatChannelFrame extends JInternalFrame
        implements ActionListener, InternalFrameListener,
        	ClientChannelListener
{
    private static final long serialVersionUID = 1L;

    private final ChatClient myChatClient;
    private final ClientChannel chan;

    private final MemberList memberList;
    private final JTextField inputField;
    private final JTextArea outputArea;

    /**
     * Constructs a new ChatChannelFrame as a wrapper around the given
     * channel.
     * 
     * @param channel the channel that this class will manage.
     */
    public ChatChannelFrame(ChatClient client, ClientChannel channel) {
        super("Channel: " + channel.getName());
        myChatClient = client;
        chan = channel;
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        c.add(eastPanel, BorderLayout.EAST);
        eastPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        memberList = new MemberList(myChatClient);
        eastPanel.add(new JScrollPane(memberList), BorderLayout.CENTER);
        JPanel southPanel = new JPanel();
        c.add(southPanel, BorderLayout.SOUTH);
        southPanel.setLayout(new GridLayout(1, 0));
        inputField = new JTextField();
        southPanel.add(inputField);
        outputArea = new JTextArea();
        c.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        inputField.addActionListener(this);
        setSize(400, 400);
        setClosable(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addInternalFrameListener(this);
        setResizable(true);
        setVisible(true);
    }
    
    private static SessionId getSessionIdAfter(byte[] message, int bytesRead) {
	int sessionIdLen = message.length - bytesRead;
	byte[] sessionIdBytes = new byte[sessionIdLen];
	System.arraycopy(message, 0, sessionIdBytes, 0, sessionIdLen);
	return SessionId.fromBytes(sessionIdBytes);
    }

    public void receivedMessage(ClientChannel channel, SessionId sender, byte[] message) {
	byte[] cmdBytes = new byte[4];
	System.arraycopy(message, 0, cmdBytes, 0, 4);
	String command = new String(cmdBytes);
	if (command.equals("CHNJ")) {
	    memberList.addClient(getSessionIdAfter(message, 4));
	} else if (command.equals("CHNL")) {
	    memberList.removeClient(getSessionIdAfter(message, 4));
	} else {
	    outputArea.append(String.format("%.8s: %s\n",
		    sender.toString(), new String(message)));
	}
    }

    public void leftChannel(ClientChannel channel) {
        if (getDesktopPane() != null) {
            getDesktopPane().remove(this);
        }
    }
    
    public void actionPerformed(ActionEvent action) {
        chan.send(inputField.getText().getBytes());
        inputField.setText("");
    }
    
    public void internalFrameOpened(InternalFrameEvent event)      { /* unused */ }
    public void internalFrameClosed(InternalFrameEvent event)      { /* unused */ }
    public void internalFrameIconified(InternalFrameEvent event)   { /* unused */ }
    public void internalFrameDeiconified(InternalFrameEvent event) { /* unused */ }
    public void internalFrameActivated(InternalFrameEvent event)   { /* unused */ }
    public void internalFrameDeactivated(InternalFrameEvent event) { /* unused */ }

    public void internalFrameClosing(InternalFrameEvent event) {
        myChatClient.leaveChannel(chan);
    }
}