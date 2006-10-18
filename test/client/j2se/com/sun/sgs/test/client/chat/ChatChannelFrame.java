package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;

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
import com.sun.sgs.client.ClientAddress;

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
    private final ByteBuffer outbuff;

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
        outbuff = ByteBuffer.allocate(1024);
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

    public void receivedMessage(ClientChannel channel, ClientAddress sender, ByteBuffer message) {
	byte[] messageBytes = new byte[4];
	message.get(messageBytes);
	String command = new String(messageBytes);
	if (command.equals("CHNJ")) {
	    memberList.addClient(ClientAddress.fromBytes(message));
	} else if (command.equals("CHNL")) {
	    memberList.removeClient(ClientAddress.fromBytes(message));
	} else {
	    message.rewind();
	    messageBytes = new byte[message.remaining()];
	    outputArea.append(String.format("%.8s: %s\n",
		    sender.toString(), new String(messageBytes)));
	}
    }

    public void leftChannel(ClientChannel channel) {
        if (getDesktopPane() != null) {
            getDesktopPane().remove(this);
        }
    }
    
    public void actionPerformed(ActionEvent action) {
	outbuff.clear();
	outbuff.put(inputField.getText().getBytes()).flip();
        chan.send(outbuff);
        inputField.setText("");
    }
    
    public void internalFrameOpened(InternalFrameEvent event) { }
    public void internalFrameClosed(InternalFrameEvent event) { }
    public void internalFrameIconified(InternalFrameEvent event) { }
    public void internalFrameDeiconified(InternalFrameEvent event) { }
    public void internalFrameActivated(InternalFrameEvent event) { }
    public void internalFrameDeactivated(InternalFrameEvent event) { }

    public void internalFrameClosing(InternalFrameEvent event) {
        myChatClient.leaveChannel(chan);
    }
}