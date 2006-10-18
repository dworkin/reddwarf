package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;

import javax.swing.DefaultListModel;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ClientAddress;

/**
 * ChatChannelFrame presents a GUI so that a user can interact with
 * a channel. The users connected to the channel are displayed in a list
 * on the right side. Messages can be sent on the channel via an input
 * area on the left side.
 * </p>
 * 
 * <p>
 * This class communicates with its channel by implementing
 * ClientChannelListener, and signing up as a listener on the channel.
 * As data arrives, and players leave or join, the appropriate call
 * backs are called.
 * </p>
 */

public class ChatChannelFrame extends JInternalFrame
        implements ClientChannelListener
{
    private static final long serialVersionUID = 1L;

    final ClientChannel chan;
    JList memberList;
    JTextField inputField;
    JTextArea outputArea;
    ByteBuffer outbuff;

    /**
     * Constructs a new ChatChannelFrame as a wrapper around the given
     * channel.
     * 
     * @param channel the channel that this class will manage.
     */
    public ChatChannelFrame(ClientChannel channel) {
        super("Channel: " + channel.getName());
        outbuff = ByteBuffer.allocate(2048);
        chan = channel;
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        c.add(eastPanel, BorderLayout.EAST);
        eastPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        memberList = new JList(new DefaultListModel());
        memberList.setCellRenderer(new ListCellRenderer() {
            JLabel text = new JLabel();

            public Component getListCellRendererComponent(JList arg0,
                    Object arg1, int arg2, boolean arg3, boolean arg4)
            {
                text.setText(String.format("%.8s", arg1.toString()));
                return text;
            }
        });
        eastPanel.add(new JScrollPane(memberList), BorderLayout.CENTER);
        JPanel southPanel = new JPanel();
        c.add(southPanel, BorderLayout.SOUTH);
        southPanel.setLayout(new GridLayout(1, 0));
        inputField = new JTextField();
        southPanel.add(inputField);
        outputArea = new JTextArea();
        c.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        inputField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chan.send(ByteBuffer.wrap(inputField.getText().getBytes()));
                inputField.setText("");
            }
        });
        setSize(400, 400);
        this.setClosable(true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.addInternalFrameListener(new InternalFrameListener() {

            public void internalFrameOpened(InternalFrameEvent event) { }
            public void internalFrameClosing(InternalFrameEvent event) { }
            public void internalFrameIconified(InternalFrameEvent event) { }
            public void internalFrameDeiconified(InternalFrameEvent event) { }
            public void internalFrameActivated(InternalFrameEvent event) { }
            public void internalFrameDeactivated(InternalFrameEvent event) { }

            public void internalFrameClosed(InternalFrameEvent event) {
                // TODO: chan.close();
        	// XXX I think this frame needs a ref to this session so we
        	// can send a direct "leave" message to the server app. -jm
            }
        });
        setResizable(true);
        setVisible(true);
    }

    /**
     * A call back from ClientChannelListener. Called when a player/user
     * joins the channel. This implementation responds by adding the
     * user to the list.
     */
    public void memberJoined(ClientAddress member) {
        DefaultListModel mdl = (DefaultListModel) memberList.getModel();
        mdl.addElement(member);
    }

    /**
     * A call back from ClientChannelListener. Called when a player/user
     * leaves the channel. This implementation responds by removing the
     * user from the user list.
     */
    public void memberLeft(ClientAddress member) {
        DefaultListModel mdl = (DefaultListModel) memberList.getModel();
        mdl.removeElement(member);
    }

    /**
     * A call back from ClientChannelListener. Called when data arrives
     * on the channel. This implementation simply dumps the data to the
     * output area as a String in the form of:
     * 
     * <pre>
     * &lt;User who sent the message&lt;: &lt;Message&lt;
     * </pre>
     */
    public void receivedMessage(ClientChannel channel, ClientAddress sender, ByteBuffer message) {
	byte[] messageBytes = new byte[message.remaining()];
	message.get(messageBytes);
	String messageString = new String(messageBytes);
	System.err.format("ChatClient: Channel %s recv [%s] \n", channel.getName(), messageString);
	if (messageString.startsWith("/joined ")) {
	    String memberString = messageString.substring(8);
	    memberJoined(ClientAddress.fromBytes(ByteBuffer.wrap(memberString.getBytes())));
	} else if (messageString.startsWith("/left ")) {
	    String memberString = messageString.substring(6);
	    memberLeft(ClientAddress.fromBytes(ByteBuffer.wrap(memberString.getBytes())));
	} else {
	    outputArea.append(String.format("%.8s: %s\n",
		    sender.toString(), messageString));
	}
    }

    /**
     * Called when the channel is closed. The frame has no need to exist
     * if the channel is closed, so it removes itself from the parent.
     */
    public void leftChannel(ClientChannel channel) {
        if (getDesktopPane() != null) {
            getDesktopPane().remove(this);
        }
    }
}