package com.sun.sgs.test.client.chat;

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
import java.net.PasswordAuthentication;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;

/**
 * The ChatClient implements a simple chat program using a Swing UI,
 * mainly for server channel testing purposes. It allows the user to
 * open arbitrary channels by name, or to use the Direct Client to
 * Client (DCC) channel.
 */
public class ChatClient extends JFrame
        implements ActionListener, SimpleClientListener, ClientChannelListener
{
    private static final long serialVersionUID = 1L;

    private final JButton loginButton;
    private final JButton openChannelButton;
    private final JButton multiDccButton;
    private final JButton serverSendButton;
    private final JLabel statusMessage;
    private final JDesktopPane desktop;

    /** The name of the global channel. */
    public static final String GLOBAL_CHANNEL_NAME = "Global";

    /** The {@link Charset} encoding for client/server messages. */
    public static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");

    /**
     * a list of clients currently connected to the
     * ChatApp on the server.
     */
    private final MemberList userList;

    /** used for communication with the server */
    private SimpleClient client;
    
    /** The listener for double-clicks on a memberlist. */
    private final MouseListener dccMouseListener;

    /** the well-known channel for Direct Client to Client communication */
    private ClientChannel dccChannel;

    private volatile int quitAttempts = 0;

    private final Map<SessionId, String> sessionNames =
        new HashMap<SessionId, String>();

    // === Constructor ==

    /**
     * Construct a ChatClient with the given {@code args}.
     */
    public ChatClient() {
        super();
        
        dccMouseListener = new DCCMouseListener(this);
        
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
        userList = new MemberList();
        userList.addMouseListener(getDCCMouseListener());
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

        multiDccButton = new JButton("Send Multi-DCC");
        multiDccButton.addActionListener(this);
        multiDccButton.setEnabled(false);

        buttonPanel.add(loginButton);
        buttonPanel.add(openChannelButton);
        buttonPanel.add(serverSendButton);
        buttonPanel.add(multiDccButton);

        addWindowListener(new QuitWindowListener(this));
        
        pack();
        //setSize(800, 600);

        setVisible(true);
    }
    
    // === GUI helper methods ===
    
    private void setButtonsEnabled(boolean enable) {
        loginButton.setEnabled(enable);
        setSessionButtonsEnabled(enable);
    }
    
    private void setSessionButtonsEnabled(boolean enable) {
        openChannelButton.setEnabled(enable);
        multiDccButton.setEnabled(enable);
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
    
    MouseListener getDCCMouseListener() {
        return dccMouseListener;
    }

    /**
     * Returns the {@link SessionId} for this client.
     *
     * @return the {@link SessionId} for this client
     */
    SessionId getSessionId() {
        return client.getSessionId();
    }

    static byte[] getMessage(byte opcode, byte[] payload) {
        byte[] message = new byte[1 + payload.length];
        message[0] = opcode;
        System.arraycopy(payload, 0, message, 1, payload.length);
        return message;
    }

    // === Handle main window GUI actions ===
    
    private void doLogin() {
	setButtonsEnabled(false);

        try {
            Properties props = new Properties();
            props.put("host", "127.0.0.1");
            props.put("port", "2502");
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
            client.send(message.getBytes(CHARSET_UTF8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doMultiDCCMessage() {
	Set<SessionId> targets = new HashSet<SessionId>();
        targets.addAll(userList.getSelectedClients());
    	if (targets.isEmpty()) {
    	    return;
    	}

        String text = getUserInput("Enter private message:");
        String message = "/dcc " + text.getBytes();
        try {
            dccChannel.send(targets, message.getBytes(CHARSET_UTF8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void doDCCMessage() {
	SessionId target = userList.getSelectedClient();
	if (target == null) {
	    return;
	}
	String text = getUserInput("Enter private message:");
        String message = "/dcc " + text.getBytes();
        try {
            dccChannel.send(target, message.getBytes(CHARSET_UTF8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void joinChannel(String channelName) {
	String cmd = "/join " + channelName;
        try {
            client.send(cmd.getBytes(CHARSET_UTF8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    void leaveChannel(ClientChannel chan) {
	String cmd = "/leave " + chan.getName();
        try {
            client.send(cmd.getBytes(CHARSET_UTF8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    static byte[] hexToBytes(String arg) {
        String hexString = arg.substring(1, arg.length() - 1);
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
        String[] split = memberString.split(":", 2);
        String idString = split[0];
        SessionId session = SessionId.fromBytes(hexToBytes(idString));
        if (split.length > 1) {
            String memberName = split[1];
            sessionNames.put(session, memberName);
        }
        userList.addClient(session);
    }

    private void userLogout(String idString) {
        System.err.println("userLogout: " + idString);
        SessionId session = SessionId.fromBytes(hexToBytes(idString));
        userList.removeClient(session);
        sessionNames.remove(session);
    }

    // Implement SimpleClientListener

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        statusMessage.setText("Status: Connected");
        setTitle("Chat Test Client: " + client.getSessionId());
        loginButton.setText("Logout");
        loginButton.setActionCommand("logout");
        setButtonsEnabled(true);
    }

    /**
     * {@inheritDoc}
     */
    public PasswordAuthentication getPasswordAuthentication() {
        statusMessage.setText("Status: Validating...");
        String login = System.getProperty("login");
        if (login == null) {
            return new ValidatorDialog(this).getPasswordAuthentication();            
        }
        String[] cred = login.split(":");
        return new PasswordAuthentication(cred[0], cred[1].toCharArray());
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
            dccChannel = channel;
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
        String messageString = new String(message, CHARSET_UTF8);
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
            String messageString = new String(message, CHARSET_UTF8);
            System.err.format("Recv on %s from %s: %s\n",
                    channel.getName(), sender, messageString);
            String[] args = messageString.split(" ", 2);
            String command = args[0];

            if (command.equals("/joined")) {
                userLogin(args[1]);
            } else if (command.equals("/left")) {
                userLogout(args[1]);
            } else if (command.equals("/members")) {
                String[] members = args[1].split(" ");
                for (String member : members) {
                    userLogin(member);
                }
            } else if (command.startsWith("/")) {
                System.err.format("Unknown command %s\n", command);
            } else {
                // Display message from another client
                JOptionPane.showMessageDialog(this,
                        messageString, "Message from " + sender,
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } else if (command.equals("multiDcc")) {
            doMultiDCCMessage();
        } else {
            System.err.format("ChatClient: Error, unknown GUI command [%s]\n",
                    command);
        }
    }

    /**
     * Listener that brings up a DCC send dialog when a {@code MemberList}
     * is double-clicked.
     */
    static final class DCCMouseListener extends MouseAdapter {
        private final ChatClient client;

        /**
         * Creates a new {@code DCCMouseListener} for the given
         * {@code ChatClient}.
         *
         * @param client the client to notify when a double-click
         *        should trigger a DCC dialog.
         */
        DCCMouseListener(ChatClient client) {
            this.client = client;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Brings up a DCC send dialog when a double-click is received.
         */
        @Override
        public void mouseClicked(MouseEvent evt) {
            if (evt.getClickCount() == 2) {
                client.doDCCMessage();
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
