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
import java.nio.ByteBuffer;
import java.util.HashSet;
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
 * <p>
 * The ChatClient implements a simple chat program using a Swing UI,
 * mainly for server channel testing purposes. It allows the user to
 * open arbitrary channels by name, or to use the Direct Client to
 * Client (DCC) channel.
 * </p>
 */
public class ChatClient extends JFrame
        implements ActionListener, SimpleClientListener, ClientChannelListener
{
    private static final long serialVersionUID = 1L;

    // TODO: move these to a common location for app and client
    static final byte OP_JOINED     = 0x4A;
    static final byte OP_LEFT       = 0x4C;
    static final byte OP_MESSAGE    = 0x4D;

    private final JButton loginButton;
    private final JButton openChannelButton;
    private final JButton multiDccButton;
    private final JButton serverSendButton;
    private final JLabel statusMessage;
    private final JDesktopPane desktop;

    private static final String GLOBAL_CHANNEL_NAME = "_Global_";

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


    // === Constructor ==

    /**
     * Construct a ChatClient with the given {@code args}.
     * 
     * @param args the commandline arguments.
     */
    public ChatClient(String[] args) {
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

    /**
     * Returns a {@link SessionId} encoded in the bytes remaining in
     * the given {@link ByteBuffer}.
     *
     * @param buffer the {@code ByteBuffer} containing an encoded
     *        {@code SessionId} from its current position to the end
     * @return the {@code SessionId} encoded in the buffer
     */
    static SessionId getSessionId(ByteBuffer buffer) {
        byte[] idBytes = new byte[buffer.remaining()];
        buffer.get(idBytes);
        return SessionId.fromBytes(idBytes);
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
            disconnected(true);
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
            client.send(message.getBytes());
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
        byte[] message = getMessage(OP_MESSAGE, text.getBytes());
        try {
            dccChannel.send(targets, message);
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
        byte[] message = getMessage(OP_MESSAGE, text.getBytes());
        try {
            dccChannel.send(target, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void joinChannel(String channelName) {
	String cmd = "/join " + channelName;
        try {
            client.send(cmd.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    void leaveChannel(ClientChannel chan) {
	String cmd = "/leave " + chan.getName();
        try {
            client.send(cmd.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void userLogin(SessionId member) {
        userList.addClient(member);
    }

    private void userLogout(SessionId member) {
        userList.removeClient(member);
    }

    // Implement SimpleClientListener

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        statusMessage.setText("Status: Connected");
        setTitle(String.format("Chat Test Client: %.8s",
                client.getSessionId().toString()));
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
    public void disconnected(boolean graceful) {
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
        if (channel.getName().equals(GLOBAL_CHANNEL_NAME)) {
            dccChannel = channel;
            return this;
        } else {
            ChatChannelFrame cframe = new ChatChannelFrame(this, channel);
            desktop.add(cframe);
            desktop.repaint();
            return cframe;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(byte[] message) {
        if (message.length < 1) {
            System.err.format("ChatClient: Error, short command\n");
        }

        ByteBuffer buf = ByteBuffer.wrap(message);
        byte opcode = buf.get();

        switch (opcode) {
        case OP_JOINED:
            userLogin(getSessionId(buf));
            break;
        case OP_LEFT:
            userLogout(getSessionId(buf));
            break;
        case OP_MESSAGE:
            byte[] msgBytes = new byte[buf.remaining()];
            buf.get(msgBytes);
            String msgString = new String(msgBytes);
            System.out.println(msgString);
            break;
        default:
            System.err.format("Unknown opcode 0x%02X\n", opcode);
            break;
        }
    }

    // Implement ClientChannelListener
    
    /**
     * {@inheritDoc}
     */
    public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
    {
	JOptionPane.showMessageDialog(this,
		new String(message),
		String.format("Message from %.8s",
			sender.toString()),
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

        /** Creates a new {@code DCCMouseListener}. */
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

        /** Creates a new {@code QuitWindowListener}. */
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
    public static void main(final String[] args) {
        new ChatClient(args);
    }

}
