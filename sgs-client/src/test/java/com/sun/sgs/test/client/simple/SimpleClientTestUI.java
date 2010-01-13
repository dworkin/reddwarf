/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
 */

package com.sun.sgs.test.client.simple;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;

import javax.swing.*;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

/**
 * A simple GUI test harness for the Client API.
 */
public class SimpleClientTestUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private final static String TITLE = "Simple Client Test";

    private SimpleClient client;

    private JTextArea messageField;

    /**
     * Create a new GUI test client and start it running.
     */
    public SimpleClientTestUI() {
        super(TITLE);

        client = new SimpleClient(new TestSimpleClientListener());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Properties props = new Properties();
                props.put("host", "");
                props.put("port", "10002");

                try {
                    client.login(props);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });

        JButton reconnectButton = new JButton("Reconnect");
        reconnectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendServerMessage("Reconnect");
            }
        });

        JButton disconnectButton = new JButton("Disconnect");
        disconnectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                client.logout(false);
            }
        });

        JPanel southPanel = new JPanel();
        southPanel.add(loginButton);
        southPanel.add(reconnectButton);
        southPanel.add(disconnectButton);

        add(doNorthLayout(), BorderLayout.NORTH);
        add(doCenterLayout(), BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        int size = 400;
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds((screenSize.width / 2) - (size / 2),
                (screenSize.height / 2) - (size / 2), 300, 400);

        setVisible(true);
    }

    private void sendServerMessage(String message) {
        try {
            sendMessage(ByteBuffer.wrap(message.getBytes("UTF-8")));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    private JPanel doCenterLayout() {
        JPanel p = new JPanel();

        JButton joinButton = new JButton("Join Channel");
        joinButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendServerMessage("Join Channel");
            }
        });

        JButton leaveButton = new JButton("Leave Channel");
        leaveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendServerMessage("Leave Channel");
            }
        });

        p.add(joinButton);
        p.add(leaveButton);

        return p;
    }

    private void sendMessage(ByteBuffer message) {
        try {
            client.send(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JPanel doNorthLayout() {
        JPanel p = new JPanel(new BorderLayout());

        messageField = new JTextArea(5, 10);
        messageField.setLineWrap(true);
        messageField.setWrapStyleWord(true);

        p.add(new JScrollPane(messageField), BorderLayout.CENTER);

        return p;
    }

    private void addMessage(String message) {
        messageField.setText(messageField.getText() + message + "\n");
    }

    private void shutdown() {
        System.exit(0);
    }

    private void setStatus(String status) {
        setTitle(TITLE + " : " + status);
    }

    private class TestSimpleClientListener implements SimpleClientListener
    {

        /**
         * {@inheritDoc}
         */
        public PasswordAuthentication getPasswordAuthentication()
        {
            JTextField usernameField = new JTextField("sten");
            JPasswordField passwordField = new JPasswordField("secret");
            JPanel authPanel = new JPanel(new GridLayout(2, 2));
            authPanel.add(new JLabel("Username:"));
            authPanel.add(usernameField);
            authPanel.add(new JLabel("Password:"));
            authPanel.add(passwordField);

            final JOptionPane optionPane = new JOptionPane(authPanel,
                    JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.OK_CANCEL_OPTION);
            JDialog dialog = optionPane.createDialog(null,
                    "Enter Credentials");
            dialog.setVisible(true);
            Integer ret = (Integer) optionPane.getValue();
            if (ret == null || ret.intValue() == JOptionPane.CANCEL_OPTION) {
                return null;
            }
            PasswordAuthentication auth = new PasswordAuthentication(
                    usernameField.getText(), passwordField.getPassword());

            return auth;
        }

        /**
         * {@inheritDoc}
         */
        public void loggedIn() {
            setStatus("Logged In");

        }

        /**
         * {@inheritDoc}
         */
        public void loginFailed(String reason) {
            setStatus("Login Failed");
        }

        /**
         * {@inheritDoc}
         */
        public void disconnected(boolean graceful, String reason) {
            setStatus("Disconnected");

        }

        /**
         * {@inheritDoc}
         */
	public ClientChannelListener joinedChannel(ClientChannel channel) {
	    return null;
	}

        /**
         * {@inheritDoc}
         */
        public void receivedMessage(ByteBuffer buf) {
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            addMessage(new String(bytes));
        }

        /**
         * {@inheritDoc}
         */
        public void reconnected() {
            setStatus("Reconnected");
        }

        /**
         * {@inheritDoc}
         */
        public void reconnecting() {
            setStatus("Reconnecting...");
        }
    }
    
    /**
     * Run the client test UI.
     *
     * @param args command-line arguments
     */
    public final static void main(String args[]) {
        new SimpleClientTestUI();
    }

}
