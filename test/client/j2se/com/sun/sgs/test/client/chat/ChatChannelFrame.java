package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;

import static com.sun.sgs.test.client.chat.ChatClient.*;

/**
 * ChatChannelFrame presents a GUI so that a user can interact with
 * a channel. The users connected to the channel are displayed in a list
 * on the right side. Messages can be sent on the channel via an input
 * area on the left side.
 */
public class ChatChannelFrame extends JInternalFrame
        implements ActionListener, ClientChannelListener
{
    private static final long serialVersionUID = 1L;

    private final ChatClient myChatClient;
    private final ClientChannel myChannel;

    private final MemberList memberList;
    private final JTextField inputField;
    private final JTextArea outputArea;

    /**
     * Constructs a new ChatChannelFrame as a wrapper around the given
     * channel.
     *
     * @param client the parent {@code ChatClient} of this frame.
     * @param channel the channel that this class will manage.
     */
    public ChatChannelFrame(ChatClient client, ClientChannel channel) {
        super("Channel: " + channel.getName());
        myChatClient = client;
        myChannel = channel;
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        c.add(eastPanel, BorderLayout.EAST);
        eastPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        memberList = new MemberList();
        memberList.addMouseListener(myChatClient.getDCCMouseListener());
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
        addInternalFrameListener(new FrameClosingListener(this));
        setResizable(true);
        setVisible(true);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Handles messages received from other clients on this channel,
     * as well as server notifications about other clients joining and
     * leaving this channel.
     */
    public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
    {
        ByteBuffer buf = ByteBuffer.wrap(message);

	byte opcode = buf.get();

        switch (opcode) {
        case OP_JOINED:
	    memberList.addClient(ChatClient.getSessionId(buf));
            break;
        case OP_LEFT:
	    memberList.removeClient(ChatClient.getSessionId(buf));
            break;
        case OP_MESSAGE: {
            byte[] contents = new byte[buf.remaining()];
            buf.get(contents);
            // The server sends us a separate, private echoback in this
            // application; those were from us originally, so use our
            // own SessionId (instead of null).
	    outputArea.append(String.format("%s: %s\n",
		    (sender != null) ? sender : myChatClient.getSessionId(),
                    new String(contents)));
            break;
        }
        default:
            System.err.format("Unknown opcode 0x%02X\n", opcode);
	}
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes this frame.
     */
    public void leftChannel(ClientChannel channel) {
        if (getDesktopPane() != null) {
            getDesktopPane().remove(this);
        }
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * Broadcasts on this channel the text entered by the user.
     */
    public void actionPerformed(ActionEvent action) {
        try {
            byte[] contents = inputField.getText().getBytes();
            byte[] message = ChatClient.getMessage(OP_MESSAGE, contents);
            myChannel.send(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
        inputField.setText("");
    }

    /**
     * Listener that requests to leave the channel when the
     * frame closes.
     */
    static final class FrameClosingListener extends InternalFrameAdapter {
        private final ChatChannelFrame frame;

        /**
         * Creates a new {@code FrameClosingListener} for the given
         * {@code ChatChannelFrame}.
         *
         * @param frame the {@code ChatChannelFrame} notify when
         *        it is closing.
         */
        FrameClosingListener(ChatChannelFrame frame) {
            this.frame = frame;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Requests that the server remove this client from this channel.
         */
        public void internalFrameClosing(InternalFrameEvent event) {
            frame.myChatClient.leaveChannel(frame.myChannel);
        }        
    }

}
