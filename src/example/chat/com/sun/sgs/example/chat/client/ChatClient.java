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
import java.net.PasswordAuthentication;
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

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;

/**
 * A simple GUI chat client that interacts with an SGS server-side app.
 * It presents an IRC-like interface, with channels, member lists, and
 * private (off-channel) messaging.
 */
public class ChatClient extends JFrame
    implements ActionListener, ListCellRenderer,
               SimpleClientListener, ClientChannelListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    private final JButton loginButton;
    private final JButton openChannelButton;
    private final JButton multiPmButton;
    private final JButton serverSendButton;
    private final JLabel statusMessage;
    private final JDesktopPane desktop;

    /** The name of the global channel. */
    public static final String GLOBAL_CHANNEL_NAME = "-GLOBAL-";

    /** The {@link Charset} encoding for client/server messages. */
    public static final String MESSAGE_CHARSET = "UTF-8";

    /** The list of clients connected to the ChatApp on the server */
    private final MultiList<SessionId> userList;

    /** used for communication with the server */
    private SimpleClient client;
    
    /** The listener for double-clicks on a memberlist. */
    private final MouseListener pmMouseListener;

    /** the well-known channel for Direct Client to Client communication */
    private ClientChannel pmChannel;

    private volatile int quitAttempts = 0;

    private final Map<SessionId, String> sessionNames =
        new HashMap<SessionId, String>();

    /** A {@code JLabel} that is reused as our ListCellRendererComponent */
    private final JLabel textLabel = new JLabel();

    /** The user name for this client's session */
    private String userName;

    // Constructor

    /**
     * Construct a ChatClient with the given {@code args}.
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
        userList = new MultiList<SessionId>(SessionId.class, this);
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

    /**
     * Returns the {@link SessionId} for this client.
     *
     * @return the {@code SessionId} for this client
     */
    SessionId getSessionId() {
        return client.getSessionId();
    }

    // Main window GUI actions

    private void doLogin() {
	setButtonsEnabled(false);
        String host = System.getProperty("ChatClient.host", "localhost");
        String port = System.getProperty("ChatClient.port", "2502");

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
        try {
            client.send(message.getBytes(MESSAGE_CHARSET));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doMultiPrivateMessage() {
	Set<SessionId> targets = new HashSet<SessionId>();
        targets.addAll(userList.getAllSelected());
    	if (targets.isEmpty()) {
    	    return;
    	}
        doPrivateMessage(targets);
    }

    private void doSinglePrivateMessage() {
	SessionId target = userList.getSelected();
	if (target == null) {
	    return;
	}
        doPrivateMessage(Collections.singleton(target));
    }

    private void doPrivateMessage(Set<SessionId> targets) {
        String input = getUserInput("Enter private message:");

        if (input.matches("^\\s*$")) {
            // Ignore empty message
            return;
        }

        String message = "/pm " + input;
        try {
            pmChannel.send(targets, message.getBytes(MESSAGE_CHARSET));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    void joinChannel(String channelName) {
	String cmd = "/join " + channelName;
        try {
            client.send(cmd.getBytes(MESSAGE_CHARSET));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    void leaveChannel(ClientChannel chan) {
	String cmd = "/leave " + chan.getName();
        try {
            client.send(cmd.getBytes(MESSAGE_CHARSET));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation renders a {@link SessionId} using a name
     * mapping maintained by the client.
     */
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus)
    {
        SessionId session = (SessionId) value;
        String text = formatSession(session);
        textLabel.setText(text);
        return textLabel;
    }

    /**
     * Nicely format a {@link SessionId} for printed display.
     *
     * @param session the {@code SessionId} to format
     * @return the formatted string
     */
    private String formatSession(SessionId session) {
        return getSessionName(session) + " [" +
            toHexString(session.toBytes()) + "]";
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

    /**
     * Returns a byte array constructed with the contents of the given
     * string, which contains a series of byte values in hex format.
     *
     * @param hexString a string to convert
     * @return the byte array corresponding to the hex-formatted string
     */
    static byte[] fromHexString(String hexString) {
        byte[] bytes = new byte[hexString.length() / 2];
        for (int i = 0; i < bytes.length; ++i) {
            String hexByte = hexString.substring(2*i, 2*i+2);
            bytes[i] = Integer.valueOf(hexByte, 16).byteValue();
        }
        return bytes;
    }

    String getSessionName(SessionId s) {
        return sessionNames.get(s);
    }

    private void userLogin(String memberString) {
        System.err.println("userLogin: " + memberString);
        addUsers(Collections.singleton(memberString));
    }

    private void addUsers(Collection<String> members) {
        List<SessionId> sessions = new ArrayList<SessionId>(members.size());
        for (String member : members) {
            String[] split = member.split(":", 2);
            String idString = split[0];
            SessionId session = SessionId.fromBytes(fromHexString(idString));
            sessions.add(session);
            if (split.length > 1) {
                String memberName = split[1];
                sessionNames.put(session, memberName);
            }
        }
        userList.addAll(sessions);
        repaint();
    }

    private void userLogout(String idString) {
        System.err.println("userLogout: " + idString);
        SessionId session = SessionId.fromBytes(fromHexString(idString));
        userList.removeItem(session);
        sessionNames.remove(session);

        // Remove the user from all our ChatChannelFrames 
        for (JInternalFrame frame : desktop.getAllFrames()) {
            if (frame instanceof ChatChannelFrame) {
                ((ChatChannelFrame) frame).memberLeft(session);
            }
        }
    }

    // Implement SimpleClientListener

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        statusMessage.setText("Status: Connected");
        SessionId session = client.getSessionId();
        sessionNames.put(session, userName);
        setTitle("Chat Test Client: " + formatSession(session));
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

        String login = System.getProperty("ChatClient.login");
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
            pmChannel = channel;
            return this;
        }

        // Other channels are handled by a new ChatChannelFrame
        ChatChannelFrame cframe = new ChatChannelFrame(this, channel);
        desktop.add(cframe);
        desktop.repaint();
        return cframe;
    }


    /**
     * {@inheritDoc}
     * <p>
     * Handles the direct {@code /echo} command.
     */
    public void receivedMessage(byte[] message) {
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

    // Implement ClientChannelListener

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
    {
        try {
            String messageString = new String(message, MESSAGE_CHARSET);
            System.err.format("Recv on %s from %s: %s\n",
                    channel.getName(), sender, messageString);
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
                System.err.format("Unknown command %s\n", command);
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
    private void pmReceived(SessionId sender, String message) {
        JOptionPane.showMessageDialog(this,
            message, "Message from " + formatSession(sender),
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

    /* TODO doc */
    static String fromMessageBytes(byte[] bytes) {
        try {
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset UTF-8 not found", e);
        }
    }

    /* TODO doc */
    static byte[] toMessageBytes(String s) {
        try {
            return s.getBytes(MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset UTF-8 not found", e);
        }
    }

    // Main
    
    /**
     * Create a new Chat Client with the given {@code args}.
     * 
     * @param args the commandline arguments.
     */
    public static void main(String[] args) {
        new ChatClient();
    }
}
