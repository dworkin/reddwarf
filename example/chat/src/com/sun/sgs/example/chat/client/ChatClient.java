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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;

/**
 * A simple GUI chat client that interacts with an SGS server-side app.
 * It presents an IRC-like interface, with channels, member lists, and
 * private (off-channel) messaging.
 * <p>
 * The {@code ChatClient} understands the following properties:
 * <ul>
 * <li><code>{@value #HOST_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_HOST} <br>
 *     The hostname of the {@code ChatApp} server.<p>
 *
 * <li><code>{@value #PORT_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_PORT} <br>
 *     The port of the {@code ChatApp} server.<p>
 *
 *
 * <li><code>{@value #LOGIN_PROPERTY}</code> <br>
 *     <i>Default:</i> none; optional <br>
 *     If specified, the colon separated username and password
 *     to use as login credentials instead of using a login dialog.
 *     For example, {@code user:pass}<p>
 *
 * </ul>
 */
public class ChatClient extends JFrame
    implements ActionListener,
               SimpleClientListener, ClientChannelListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    private final JButton loginButton;
    private final JButton openChannelButton;
    private final JButton pmButton;
    private final JButton serverSendButton;
    private final JLabel statusMessage;
    private final JDesktopPane desktop;

    /** The name of the global channel */
    public static final String GLOBAL_CHANNEL_NAME = "-GLOBAL-";

    /** The name of the host property */
    public static final String HOST_PROPERTY = "ChatClient.host";

    /** The default hostname */
    public static final String DEFAULT_HOST = "localhost";

    /** The name of the port property */
    public static final String PORT_PROPERTY = "ChatClient.port";

    /** The default port */
    public static final String DEFAULT_PORT = "2502";

    /** The name of the login property */
    public static final String LOGIN_PROPERTY = "ChatClient.login";

    /** The {@link Charset} encoding for client/server messages */
    public static final String MESSAGE_CHARSET = "UTF-8";

    /** The list of clients connected to the ChatApp on the server */
    private final MultiList<String> userList;

    /** used for communication with the server */
    private SimpleClient client;
    
    /** The listener for double-clicks on a memberlist */
    private final MouseListener pmMouseListener;

    /** How many times this client has tried to quit */
    private volatile int quitAttempts = 0;

    /** The user name for this client's session */
    private String userName;

    /** A map of channel names to their channel frames, so we can update
     *  membership lists.
     */
    private Map<String, ChatChannelFrame> channelMap = 
            new HashMap<String, ChatChannelFrame>();
    
    // Constructor

    /**
     * Creates a new {@code ChatClient}.
     */
    public ChatClient() {
        super();
        
        pmMouseListener = new PMMouseListener(this);
        
        setTitle("Chat Test Client");
        Container c = getContentPane();
        c.setLayout(new BorderLayout());

        JPanel buttonPanel = new JPanel();
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new GridLayout(0, 1));
        statusMessage = new JLabel("Status: Not Connected");
        southPanel.add(buttonPanel);
        southPanel.add(statusMessage);
        c.add(southPanel, BorderLayout.SOUTH);

        desktop = new JDesktopPane();
        c.add(desktop, BorderLayout.CENTER);

        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        eastPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        userList = new MultiList<String>(String.class);
        userList.addMouseListener(getPMMouseListener());
        eastPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        c.add(eastPanel, BorderLayout.EAST);

        buttonPanel.setLayout(new GridLayout(1, 0));
        loginButton = new JButton("Login");
        loginButton.setActionCommand("login");
        loginButton.addActionListener(this);
        loginButton.setToolTipText("Click to log in a new user");

        openChannelButton = new JButton("Open Channel");
        openChannelButton.setActionCommand("openChannel");
        openChannelButton.addActionListener(this);
        openChannelButton.setEnabled(false);
        openChannelButton.setToolTipText("Click to join a channel");

        serverSendButton = new JButton("Send to Server");
        serverSendButton.setActionCommand("directSend");
        serverSendButton.addActionListener(this);
        serverSendButton.setEnabled(false);
        serverSendButton.setToolTipText(
                "Click to send a message directly to the server");

        pmButton = new JButton("Send PM");
        pmButton.setActionCommand("sendPM");
        pmButton.addActionListener(this);
        pmButton.setEnabled(false);
        pmButton.setToolTipText(
                "Click to send a private message to the selected user " + "" +
                "in main frame, or double-click on the user in the main frame");

        buttonPanel.add(loginButton);
        buttonPanel.add(openChannelButton);
        buttonPanel.add(serverSendButton);
        buttonPanel.add(pmButton);

        addWindowListener(new QuitWindowListener(this));
        setSize(1000, 720);
        setVisible(true);
    }

    // Main

    /**
     * Runs a new {@code ChatClient} application.
     * 
     * @param args the commandline arguments (not used)
     */
    public static void main(String[] args) {
        new ChatClient();
    }

    // GUI helper methods

    private void setButtonsEnabled(boolean enable) {
        loginButton.setEnabled(enable);
        setSessionButtonsEnabled(enable);
    }
    
    private void setSessionButtonsEnabled(boolean enable) {
        openChannelButton.setEnabled(enable);
        pmButton.setEnabled(enable);
        serverSendButton.setEnabled(enable);
    }

    private String getUserInput(String prompt) {
	setButtonsEnabled(false);
	try {
	    return JOptionPane.showInputDialog(this, prompt);
	} finally {
	    setButtonsEnabled(true);
	}
    }
    
    private void showMessage(String message) {
        setButtonsEnabled(false);
        try {
            JOptionPane.showMessageDialog(this, message);
        } finally {
            setButtonsEnabled(true);
        }
    }
    
    MouseListener getPMMouseListener() {
        return pmMouseListener;
    }

    // Main window GUI actions

    private void doLogin() {
	setButtonsEnabled(false);
        String host = System.getProperty(HOST_PROPERTY, DEFAULT_HOST);
        String port = System.getProperty(PORT_PROPERTY, DEFAULT_PORT);

        try {
            Properties props = new Properties();
            props.put("host", host);
            props.put("port", port);
            client = new SimpleClient(this);
            client.login(props);
            // TODO: enable the loginButton as a "Cancel Login" action.
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void doLogout() {
	setButtonsEnabled(false);
        client.logout(false);
    }

    private void doQuit() {
        ++quitAttempts;
        
        if (client == null || (! client.isConnected())) {
            disconnected(true, "Quit");
            return;
        }
        
	switch (quitAttempts) {
	case 1:
	    client.logout(false);
	    break;
	case 2:
	    client.logout(true);
	    break;
	default:
	    System.exit(1);
	    break;
	}
    }

    private void doOpenChannel() {
	joinChannel(getUserInput("Enter channel name:"));
    }

    private void doServerMessage() {
	String message = "/ping " + getUserInput("Enter server message:");

        try {
            client.send(toMessageBuffer(message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doSinglePrivateMessage() {
        String target = userList.getSelected();
	if (target == null) {
            showMessage("Must select a user in main Chat Test panel");
	    return;
	}
        String input = getUserInput("Enter private message to " + target + " :");

        if ((input == null) || input.matches("^\\s*$")) {
            // Ignore empty message
            showMessage("Empty message not sent to " + target);
            return;
        }

        String message = "/pm " + 
                         target + " " +
                         input;
        try {
            client.send(toMessageBuffer(message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void joinChannel(String channelName) {

        if ((channelName == null) || channelName.matches("^\\s*$")) {
            // Ignore empty channel name
            showMessage("Channel name must be provided.");
            return;
        }

	String cmd = "/join " + channelName;
        try {
            client.send(toMessageBuffer(cmd));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    void leaveChannel(ClientChannel channel) {
	String cmd = "/leave " + channel.getName();
        try {
            client.send(toMessageBuffer(cmd));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String getUserName() {
        return userName;
    }
    
    private void userLogin(String memberString) {
        System.err.println("userLogin: " + memberString);
        addUsers(Collections.singleton(memberString));
    }

    private void addUsers(Collection<String> members) {
        userList.addAllItems(members);
        userList.invalidate();
        repaint();
    }

    private void userLogout(String idString) {
        System.err.println("userLogout: " + idString);
        userList.removeItem(idString);

        // Remove the user from all our ChatChannelFrames 
        for (JInternalFrame frame : desktop.getAllFrames()) {
            if (frame instanceof ChatChannelFrame) {
                ((ChatChannelFrame) frame).memberLeft(idString);
            }
        }
    }

    // Implement SimpleClientListener

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        statusMessage.setText("Status: Connected");
        setTitle("Chat Test Client: " + userName);
        // Tell the server we're ready to join the global channel
        String cmd = "/join_global ";
        try {
            client.send(toMessageBuffer(cmd));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        loginButton.setText("Logout");
        loginButton.setActionCommand("logout");
        setButtonsEnabled(true);
    }

    /**
     * {@inheritDoc}
     */
    public PasswordAuthentication getPasswordAuthentication() {
        statusMessage.setText("Status: Validating...");
        PasswordAuthentication auth;

        String login = System.getProperty(LOGIN_PROPERTY);
        if (login == null) {
            Future<PasswordAuthentication> future =
                new LoginDialog(this).requestLogin();
            try {
                auth = future.get();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            String[] cred = login.split(":");
            auth = new PasswordAuthentication(cred[0], cred[1].toCharArray());
        }
        userName = auth.getUserName();
        return auth;
    }

    /**
     * {@inheritDoc}
     */
    public void loginFailed(String reason) {
        // This will be immediately followed by a disconnected message.
        showMessage("Login failed (" + reason + ")");
        statusMessage.setText("Status: Login failed (" + reason + ")");
        loginButton.setText("Login");
        loginButton.setActionCommand("login");
        loginButton.setEnabled(true);
    }

    /**
     * {@inheritDoc}
     */
    public void disconnected(boolean graceful, String reason) {
        setButtonsEnabled(false);
        statusMessage.setText("Status: logged out");
        if (quitAttempts > 0) {
            dispose();
        } else {

            // Reset title bar
            setTitle("Chat Test Client: [disconnected]");

            // Close all channel frames
            for (JInternalFrame frame : desktop.getAllFrames()) {
                if (frame instanceof ChatChannelFrame) {
                    frame.dispose();
                    desktop.remove(frame);
                }
            }

            // Clear our member list
            userList.removeAllItems();

            // Reset the login button
            loginButton.setText("Login");
            loginButton.setActionCommand("login");
            loginButton.setEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void reconnecting() {
        statusMessage.setText("Status: Reconnecting");
        setSessionButtonsEnabled(false);
    }

    /**
     * {@inheritDoc}
     */
    public void reconnected() {
        statusMessage.setText("Status: Reconnected");
        setSessionButtonsEnabled(true);
    }

    /**
     * {@inheritDoc}
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        // ChatClient handles the global channel
        if (channel.getName().equals(GLOBAL_CHANNEL_NAME)) {
            return this;
        }
        // Other channels are handled by a new ChatChannelFrame
        ChatChannelFrame cframe = new ChatChannelFrame(this, channel);
        channelMap.put(channel.getName(), cframe);
        desktop.add(cframe);
        desktop.repaint();
        return cframe;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Handles the direct messages.
     */
    public void receivedMessage(ByteBuffer message) {
        String messageString = fromMessageBuffer(message);
        System.err.format("Recv direct: %s\n", messageString);
        String[] args = messageString.split(" ", 2);
        String command = args[0];

        if (command.equals("/pong")) {
            System.out.println(args[1]);
        } else if (command.equals("/pm")) {
            pmReceived(args[1]);
        } else if (command.equals("/members")) {
            args = args[1].split(" ", 2);
            if (args[0].equals(GLOBAL_CHANNEL_NAME)) {
                List<String> members = Arrays.asList(args[1].split(" "));
                if (! members.isEmpty()) {
                    addUsers(members);
                } 
            } else {
                ChatChannelFrame frame = channelMap.get(args[0]);
                frame.updateMembers(args[1]);
            }
        } else if (command.equals("/loginFailed")) {
            showMessage("Login failed: " + args[1]);
        } else {
            System.err.format("Unknown command: %s\n", messageString);
        }
    }

    // Implement ClientChannelListener

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(ClientChannel channel, ByteBuffer message) {
        try {
            String messageString = fromMessageBuffer(message);
            System.err.format("Recv on %s: %s\n",
                channel.getName(), messageString);
            String[] args = messageString.split(" ", 2);
            String command = args[0];

            if (command.equals("/joined")) {
                userLogin(args[1]);
            } else if (command.equals("/left")) {
                userLogout(args[1]);
            } else if (command.equals("/members")) {
                List<String> members = Arrays.asList(args[1].split(" "));
                if (! members.isEmpty()) {
                    addUsers(members);
                } 
            } else if (command.startsWith("/")) {
                System.err.format("Unknown command: %s\n", command);
            } else {
                System.err.format("Not a command: %s\n", messageString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Displays a private message from another client in a popup window.
     *
     * @param message the message received
     */
    private void pmReceived(String message) {
        // The sender has been encoded in the msg by the server
        String[] args = message.split(" ", 3);
        JOptionPane.showMessageDialog(this,
            args[2], "Message from " + args[0],
            JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    public void leftChannel(ClientChannel channel) {
	System.err.format("ChatClient: Error, kicked off channel [%s]\n",
	    channel.getName());
    }

    // Implement ActionListener

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent action) {
	final String command = action.getActionCommand();
        if (command.equals("login")) {
            doLogin();
        } else if (command.equals("logout")) {
            doLogout();
        } else if (command.equals("openChannel")) {
            doOpenChannel();
        } else if (command.equals("directSend")) {
            doServerMessage();
        } else if (command.equals("sendPM")) {
            doSinglePrivateMessage();
        } else {
            System.err.format("ChatClient: Error, unknown GUI command [%s]\n",
                    command);
        }
    }

    /**
     * Listener that brings up a PM send dialog when a {@code MultiList}
     * is double-clicked.
     */
    static final class PMMouseListener extends MouseAdapter {
        private final ChatClient client;

        /**
         * Creates a new {@code PMMouseListener} for the given
         * {@code ChatClient}.
         *
         * @param client the client to notify when a double-click
         *        should trigger a PM dialog.
         */
        PMMouseListener(ChatClient client) {
            this.client = client;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Brings up a PM send dialog when a double-click is received.
         */
        @Override
        public void mouseClicked(MouseEvent evt) {
            if (evt.getClickCount() == 2) {
                client.doSinglePrivateMessage();
            }
        }
    }

    /**
     * Listener that quits the {@code ChatClient} when the window
     * is closed.
     */
    static final class QuitWindowListener extends WindowAdapter {
        private final ChatClient client;

        /**
         * Creates a new {@code QuitWindowListener} for the given
         * {@code ChatClient}.
         *
         * @param client the client to notify on windowClosing
         */
        QuitWindowListener(ChatClient client) {
            this.client = client;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public void windowClosing(WindowEvent e) {
            client.doQuit();
        }
    }

    /**
     * Decodes the given {@code buffer} into a message string.
     *
     * @param buffer the encoded message
     * @return the decoded message string
     */
    static String fromMessageBuffer(ByteBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }

    /**
     * Encodes the given message string into a {@link ByteBuffer}.
     *
     * @param s the message string to encode
     * @return the encoded message as a {@code ByteBuffer}
     */
    static ByteBuffer toMessageBuffer(String s) {
        try {
            return ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }
}
