/*
 * Copyright (c) 2007-2009, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.tutorial.client.lesson1;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.UnsupportedEncodingException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

/**
 * A simple GUI client that interacts with an SGS server-side app.
 * It presents a basic chat interface with an output area and input
 * field.
 * <p>
 * The client understands the following properties:
 * <ul>
 * <li><code>{@value #HOST_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_HOST} <br>
 *     The hostname of the server.<p>
 *
 * <li><code>{@value #PORT_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_PORT} <br>
 *     The port that the server is listening on.<p>
 *
 * </ul>
 */
public class HelloUserClient extends JFrame
    implements SimpleClientListener, ActionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The name of the host property. */
    public static final String HOST_PROPERTY = "tutorial.host";

    /** The default hostname. */
    public static final String DEFAULT_HOST = "localhost";

    /** The name of the port property. */
    public static final String PORT_PROPERTY = "tutorial.port";

    /** The default port. */
    public static final String DEFAULT_PORT = "1139";

    /** The message encoding. */
    public static final String MESSAGE_CHARSET = "UTF-8";

    /** The output area for chat messages. */
    protected final JTextArea outputArea;

    /** The input field for the user to enter a chat message. */
    protected final JTextField inputField;

    /** The panel that wraps the input field and any other UI. */
    protected final JPanel inputPanel;

    /** The status indicator. */
    protected final JLabel statusLabel;

    /** The {@link SimpleClient} instance for this client. */
    protected final SimpleClient simpleClient;

    /** The random number generator for login names. */
    private final Random random = new Random();

    // Main

    /**
     * Runs an instance of this client.
     *
     * @param args the command-line arguments (unused)
     */
    public static void main(String[] args) {
        new HelloUserClient().login();
    }

    // HelloUserClient methods

    /**
     * Creates a new client UI.
     */
    public HelloUserClient() {
        this(HelloUserClient.class.getSimpleName());
    }

    /**
     * Creates a new client UI with the given window title.
     *
     * @param title the title for the client's window
     */
    protected HelloUserClient(String title) {
        super(title);
        Container c = getContentPane();
        JPanel appPanel = new JPanel();
        appPanel.setFocusable(false);
        c.setLayout(new BorderLayout());
        appPanel.setLayout(new BorderLayout());
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFocusable(false);
        appPanel.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        inputField = new JTextField();
        inputField.addActionListener(this);
        inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());
        populateInputPanel(inputPanel);
        inputPanel.setEnabled(false);
        appPanel.add(inputPanel, BorderLayout.SOUTH);
        c.add(appPanel, BorderLayout.CENTER);
        statusLabel = new JLabel();
        statusLabel.setFocusable(false);
        setStatus("Not Started");
        c.add(statusLabel, BorderLayout.SOUTH);
        setSize(640, 480);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setVisible(true);
        simpleClient = new SimpleClient(this);
    }

    /**
     * Allows subclasses to populate the input panel with
     * additional UI elements.  The base implementation
     * simply adds the input text field to the center of the panel.
     *
     * @param panel the panel to populate
     */
    protected void populateInputPanel(JPanel panel) {
        panel.add(inputField, BorderLayout.CENTER);
    }

    /**
     * Appends the given message to the output text pane.
     *
     * @param x the message to append to the output text pane
     */
    protected void appendOutput(String x) {
        outputArea.append(x + "\n");
    }

    /**
     * Initiates asynchronous login to the SGS server specified by
     * the host and port properties.
     */
    protected void login() {
        String host = System.getProperty(HOST_PROPERTY, DEFAULT_HOST);
        String port = System.getProperty(PORT_PROPERTY, DEFAULT_PORT);

        try {
            Properties connectProps = new Properties();
            connectProps.put("host", host);
            connectProps.put("port", port);
            simpleClient.login(connectProps);
        } catch (Exception e) {
            e.printStackTrace();
            disconnected(false, e.getMessage());
        }
    }

    /**
     * Displays the given string in this client's status bar.
     *
     * @param status the status message to set
     */
    protected void setStatus(String status) {
        appendOutput("Status Set: " + status);
        statusLabel.setText("Status: " + status);
    }

    /**
     * Encodes a {@code String} into a {@link ByteBuffer}.
     *
     * @param s the string to encode
     * @return the {@code ByteBuffer} which encodes the given string
     */
    protected static ByteBuffer encodeString(String s) {
        try {
            return ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required character set " + MESSAGE_CHARSET +
                " not found", e);
        }
    }

    /**
     * Decodes a {@link ByteBuffer} into a {@code String}.
     *
     * @param buf the {@code ByteBuffer} to decode
     * @return the decoded string
     */
    protected static String decodeString(ByteBuffer buf) {
        try {
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required character set " + MESSAGE_CHARSET +
                " not found", e);
        }
    }

    /**
     * Returns the user-supplied text from the input field, and clears
     * the field to prepare for more input.
     *
     * @return the user-supplied text from the input field
     */
    protected String getInputText() {
        try {
            return inputField.getText();
        } finally {
            inputField.setText("");
        }
    }

    // Implement SimpleClientListener

    /**
     * {@inheritDoc}
     * <p>
     * Returns dummy credentials where user is "guest-&lt;random&gt;"
     * and the password is "guest."  Real-world clients are likely
     * to pop up a login dialog to get these fields from the player.
     */
    public PasswordAuthentication getPasswordAuthentication() {
        String player = "guest-" + random.nextInt(1000);
        setStatus("Logging in as " + player);
        String password = "guest";
        return new PasswordAuthentication(player, password.toCharArray());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Enables input and updates the status message on successful login.
     */
    public void loggedIn() {
        inputPanel.setEnabled(true);
        setStatus("Logged in");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Updates the status message on failed login.
     */
    public void loginFailed(String reason) {
        setStatus("Login failed: " + reason);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Disables input and updates the status message on disconnect.
     */
    public void disconnected(boolean graceful, String reason) {
        inputPanel.setEnabled(false);
        setStatus("Disconnected: " + reason);
    }

    /**
     *  {@inheritDoc}
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        return new NullClientChannelListener();
    }
    /**
     * {@inheritDoc}
     * <p>
     * Decodes the message data and adds it to the display.
     */
    public void receivedMessage(ByteBuffer message) {
        appendOutput("Server sent: " + decodeString(message));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Updates the status message on successful reconnect.
     */
    public void reconnected() {
        setStatus("reconnected");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Updates the status message when reconnection is attempted.
     */
    public void reconnecting() {
        setStatus("reconnecting");
    }

    // Implement ActionListener

    /**
     * {@inheritDoc}
     * <p>
     * Encodes the string entered by the user and sends it to the server.
     */
    public void actionPerformed(ActionEvent event) {
        if (! simpleClient.isConnected())
            return;

        String text = getInputText();
        send(text);
    }

    /**
     * Encodes the given text and sends it to the server.
     * 
     * @param text the text to send.
     */
    protected void send(String text) {
        try {
            ByteBuffer message = encodeString(text);
            simpleClient.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     *  A ClientChannelListener that does nothing at all (this basic
     *  client does not support channels).
     */
    private static class NullClientChannelListener 
            implements ClientChannelListener 
    {
        /** {@inheritDoc} */
        public void leftChannel(ClientChannel channel) { 
            System.out.println("Unexepected call to leftChannel");
        }
        /** {@inheritDoc} */
        
        public void receivedMessage(ClientChannel channel, ByteBuffer message) {
            System.out.println("Unexepected call to receivedMessage");
            
        }
    }
}
