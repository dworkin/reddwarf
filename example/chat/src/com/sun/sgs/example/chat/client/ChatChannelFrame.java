/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.chat.client;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.math.BigInteger;
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

/**
 * ChatChannelFrame presents a GUI so that a user can interact with
 * a channel. The users connected to the channel are displayed in a list
 * on the right side. Messages can be sent on the channel via an input
 * area on the bottom of the left side.
 */
public class ChatChannelFrame extends JInternalFrame
        implements ActionListener, ClientChannelListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@code ChatClient} that is the parent of this frame. */
    private final ChatClient myChatClient;

    /** The channel associated with this frame. */
    private final ClientChannel myChannel;

    /** The {@code MultiList} containing this channel's members. */
    private final MultiList<BigInteger> multiList;

    /** The input field. */
    private final JTextField inputField;

    /** The output area for channel messages. */
    private final JTextArea outputArea;

    /**
     * Constructs a new {@code ChatChannelFrame} as a wrapper around the given
     * channel.
     *
     * @param client the parent {@code ChatClient} of this frame.
     * @param channelName the channel that this class will manage.
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
        multiList = new MultiList<BigInteger>(BigInteger.class, client);
        multiList.addMouseListener(myChatClient.getPMMouseListener());
        eastPanel.add(new JScrollPane(multiList), BorderLayout.CENTER);
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
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        addInternalFrameListener(new FrameClosingListener(this));
        setResizable(true);
        setVisible(true);
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(ClientChannel channel, ByteBuffer message) {
        try {
            String messageString = ChatClient.fromMessageBuffer(message);
            System.err.format("Recv on %s: %s\n",
                    channel.getName(), messageString);
            String[] args = messageString.split(" ", 2);
            String command = args[0];

            if (command.equals("/joined")) {
                BigInteger memberId = new BigInteger(args[1], 16);
                multiList.addItem(memberId);
            } else if (command.equals("/left")) {
                BigInteger memberId = new BigInteger(args[1], 16);
                memberLeft(memberId);
            } else if (command.equals("/members")) {
                String[] members = args[1].split("\\s+");
                for (String member : members) {
                    BigInteger memberId = new BigInteger(member, 16);
                    multiList.addItem(memberId);
                }
            } else if (command.startsWith("/")) {
                System.err.format("Unknown command %s\n", command);
            } else {
                BigInteger memberId = new BigInteger(command, 16);
                outputArea.append(String.format("%s: %s\n",
                        myChatClient.getSessionName(memberId), args[1]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * * {@inheritDoc}
     * <p>
     * Closes this frame.
     */
    public void leftChannel(ClientChannel channel) {
        dispose();
        if (getDesktopPane() != null) {
            getDesktopPane().remove(this);
        }
    }

    /**
     * Updates the channel list when a member leaves.
     *
     * @param member the member who left this channel
     */
    void memberLeft(BigInteger member) {
        multiList.removeItem(member);        
    }

    /**
     * {@inheritDoc}
     * <p>
     * Broadcasts on this channel the text entered by the user.
     */
    public void actionPerformed(ActionEvent action) {
        try {
            String message = inputField.getText();
            ByteBuffer msgBuf = ChatClient.toMessageBuffer(message);
            myChannel.send(msgBuf);
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
        @Override
        public void internalFrameClosing(InternalFrameEvent event) {
            frame.myChatClient.leaveChannel(frame.myChannel);
        }        
    }
}
