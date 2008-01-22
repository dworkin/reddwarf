/*
 * Copyright 2007 Sun Microsystems, Inc.
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
import java.awt.Component;
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
import java.math.BigInteger;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;

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
    implements ActionListener, ListCellRenderer, SimpleClientListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    private final JButton loginButton;
    private final JButton openChannelButton;
    private final JButton multiPmButton;
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
    private final MultiList<ChatMember> userList;

    private final Map<BigInteger, ChatMember> membersById =
        new HashMap<BigInteger, ChatMember>();

    /** used for communication with the server */
    private SimpleClient client;
    
    /** The listener for double-clicks on a memberlist */
    private final MouseListener pmMouseListener;

    /** How many times this client has tried to quit */
    private volatile int quitAttempts = 0;

    /** A {@code JLabel} that is reused as our ListCellRendererComponent */
    private final JLabel textLabel = new JLabel();

    /** The chatMember for this client's session */
    private ChatMember chatMember;

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
        userList = new MultiList<ChatMember>(ChatMember.class, this);
        userList.addMouseListener(getPMMouseListener());
        eastPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        c.add(eastPanel, BorderLayout.EAST);

        buttonPanel.setLayout(new GridLayout(1, 0));
        loginButton = new JButton("Login");
        loginButton.setActionCommand("login");
        loginButton.addActionListener(this);

        openChannelButton = new JButton("Open Channel");
        openChannelButton.setActionCommand("openChannel");
        openChannelButton.addActionListener(this);
        openChannelButton.setEnabled(false);

        serverSendButton = new JButton("Send to Server");
        serverSendButton.setActionCommand("directSend");
        serverSendButton.addActionListener(this);
        serverSendButton.setEnabled(false);

        multiPmButton = new JButton("Send Multi-PM");
        multiPmButton.setActionCommand("multiPm");
        multiPmButton.addActionListener(this);
        multiPmButton.setEnabled(false);

        buttonPanel.add(loginButton);
        buttonPanel.add(openChannelButton);
        buttonPanel.add(serverSendButton);
        buttonPanel.add(multiPmButton);

        addWindowListener(new QuitWindowListener(this));
        setSize(1000, 720);
        setVisible(true);
    }

    // Main

    /**
     * Runs a new {@code ChatClient} application.
     * 
     * @param args the command line arguments (not used)
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
        multiPmButton.setEnabled(enable);
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
	String message = getUserInput("Enter server message:");

        if (message == null)
            return;

        try {
            client.send(toMessageBytes(message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void broadcast(String channelName, String message) {
        try {
            client.send(toMessageBytes(
                "/broadcast " + channelName + " " + message));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Deliver to our own receivedMessage, since server won't echo.
        // TODO
        //receivedMessage(channelName, getSessionId(), message);
    }
    private void doMultiPrivateMessage() {
	Set<ChatMember> targets = new HashSet<ChatMember>();
        targets.addAll(userList.getAllSelected());
    	if (targets.isEmpty()) {
    	    return;
    	}
        doPrivateMessage(targets);
    }

    private void doSinglePrivateMessage() {
	ChatMember target = userList.getSelected();
	if (target == null) {
	    return;
	}
        doPrivateMessage(Collections.singleton(target));
    }

    private void doPrivateMessage(Set<ChatMember> targets) {
        String input = getUserInput("Enter private message:");

        if ((input == null) || input.matches("^\\s*$")) {
            // Ignore empty message
            return;
        }

        StringBuffer s = new StringBuffer();
        s.append("/pm");
        for (ChatMember target : targets) {
            s.append(" ");
            s.append(target);
        }
        s.append("::");
        s.append(input);
        try {
            client.send(toMessageBytes(s.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void joinChannel(String channelName) {

        if ((channelName == null) || channelName.matches("^\\s*$")) {
            // Ignore empty channel name
            return;
        }

	String cmd = "/join " + channelName;
        try {
            client.send(toMessageBytes(cmd));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    void leaveChannel(String channelName) {
	String cmd = "/leave " + channelName;
        try {
            client.send(toMessageBytes(cmd));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation renders a {@link ChatMember} using a name
     * mapping maintained by the client.
     */
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus)
    {
        ChatMember member = (ChatMember) value;
        String text = formatSession(member);
        textLabel.setText(text);
        return textLabel;
    }

    /**
     * Nicely format a {@link ChatMember} for printed display.
     *
     * @param member the {@code ChatMember} to format
     * @return the formatted string
     */
    private String formatSession(ChatMember member) {
        return member.toString();
    }

    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.
     *
     * @param bytes a byte array to convert
     * @return the converted byte array as a hex-formatted string
     */
    public static String toHexString(byte[] bytes) {
        StringBuilder buf = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            buf.append(String.format("%02X", b));
        }
        return buf.toString();
    }

    private void userLogin(String memberString) {
        System.err.println("userLogin: " + memberString);
        addUsers(Collections.singleton(memberString));
    }

    private void addUsers(Collection<String> memberStrings) {
        List<ChatMember> members =
            new ArrayList<ChatMember>(memberStrings.size());
        for (String memberString : memberStrings) {
            String[] split = memberString.split(":", 2);
            BigInteger id = idFromString(split[0]);
            String name = split[1];
            ChatMember member = new ChatMember(name, id);
            membersById.put(id, member);
            members.add(member);
        }
        userList.addAllItems(members);
        userList.invalidate();
        repaint();
    }

    private void userLogout(String idString) {
        System.err.println("userLogout: " + idString);
        BigInteger id = idFromString(idString);
        ChatMember member = membersById.get(id);
        userList.removeItem(member);

        // Remove the user from all our ChatChannelFrames 
        for (JInternalFrame frame : desktop.getAllFrames()) {
            if (frame instanceof ChatChannelFrame) {
                ((ChatChannelFrame) frame).memberLeft(member);
            }
        }

        membersById.remove(member);
    }

    // Implement SimpleClientListener
    
    BigInteger idFromString(String s) {
        return new BigInteger(s, 16);
    }

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        statusMessage.setText("Status: Connected");
    }

    // FIXME: call this with a special "logged in" app message
    private void reallyLoggedIn(ChatMember member) {
        this.chatMember = member;
        setTitle("Chat Test Client: " + formatSession(member));
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
        return auth;
    }

    /**
     * {@inheritDoc}
     */
    public void loginFailed(String reason) {
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
            
            membersById.clear();

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

    // FIXME use this!
    private void joinedChannel(String channelName) {
        // Other channels are handled by a new ChatChannelFrame
        ChatChannelFrame cframe = new ChatChannelFrame(this, channelName);
        desktop.add(cframe);
        desktop.repaint();
    }


    /**
     * {@inheritDoc}
     * <p>
     * Handles the direct {@code /pong} reply.
     */
    public void receivedMessage(ByteBuffer message) {
        String messageString = fromMessageBytes(message);
        System.err.format("Recv direct: %s\n", messageString);
        String[] args = messageString.split(" ", 2);
        String command = args[0];

        if (command.equals("/pong")) {
            System.out.println(args[1]);            
        } else {
            System.err.format("Unknown command: %s\n", messageString);
        }
    }

    // FIXME use this!  Look up the appropriate ChatChannelFrame and call it!
    private void receivedChannelMessage(String channelName, ChatMember sender,
            ByteBuffer message)
    {
        try {
            String messageString = fromMessageBytes(message);
            System.err.format("Recv on %s from %s: %s\n",
                channelName, sender, messageString);
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
            } else if (command.equals("/pm")) {
                pmReceived(sender, args[1]);
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
     * @param sender the sender of the message
     * @param message the message received
     */
    private void pmReceived(ChatMember sender, String message) {
        JOptionPane.showMessageDialog(this,
            message, "Message from " + formatSession(sender),
            JOptionPane.INFORMATION_MESSAGE);
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
        } else if (command.equals("multiPm")) {
            doMultiPrivateMessage();
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
     * Decodes the given {@code ByteBuffer} into a message string.
     *
     * @param buf the encoded message
     * @return the decoded message string
     */
    static String fromMessageBytes(ByteBuffer buf) {
        try {
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset UTF-8 not found", e);
        }
    }

    /**
     * Encodes the given message string into a byte array.
     *
     * @param s the message string to encode
     * @return the encoded message as a byte array
     */
    static ByteBuffer toMessageBytes(String s) {
        try {
            return ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset UTF-8 not found", e);
        }
    }
}
