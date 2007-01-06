package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.PasswordAuthentication;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

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
        implements ActionListener, WindowListener,
        	SimpleClientListener, ClientChannelListener
{
    private static final long serialVersionUID = 1L;

    private final JButton loginButton;
    private final JButton openChannelButton;
    private final JButton multiDccButton;
    private final JButton serverSendButton;
    private final JLabel statusMessage;
    private final JDesktopPane desktop;

    private static final String DCC_CHANNEL_NAME = "__DCC_Chan";

    /** a list of clients currently connected to the
     *  ChatApp on the server.
     */
    private final MemberList userList;

    /**  used for communication with the server */
    private SimpleClient client;
    
    /** the well-known channel for Direct Client to Client communication */
    private ClientChannel dccChannel;

    private volatile int quitAttempts = 0;

    // === Constructor ==
 
    public ChatClient(String[] args) {
        super();
        
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
        userList = new MemberList(this);

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

        pack();
        setSize(800, 600);

        addWindowListener(this);
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
        client.send(message.getBytes());
    }

    private void doMultiDCCMessage() {
	Collection<SessionId> targets = userList.getSelectedClients();
    	if (targets == null || targets.isEmpty()) {
    	    return;
    	}

        String message = getUserInput("Enter private message:");
        dccChannel.send(targets, message.getBytes());
    }

    void doDCCMessage() {
	SessionId target = userList.getSelectedClient();
	if (target == null) {
	    return;
	}
	String message = getUserInput("Enter private message:");
        dccChannel.send(target, message.getBytes());
    }

    void joinChannel(String channelName) {
	String cmd = "/join " + channelName;
	client.send(cmd.getBytes());
    }
 
    void leaveChannel(ClientChannel chan) {
	String cmd = "/leave " + chan.getName();
	client.send(cmd.getBytes());
    }

    private void userLogin(SessionId member) {
        userList.addClient(member);
    }

    private void userLogout(SessionId member) {
        userList.removeClient(member);
    }

    // === SimpleClientListener ===

    public void loggedIn() {
        statusMessage.setText("Status: Connected");
        setTitle(String.format("Chat Test Client: %.8s",
                client.getSessionId().toString()));
        loginButton.setText("Logout");
        loginButton.setActionCommand("logout");
        setButtonsEnabled(true);
    }

    public PasswordAuthentication getPasswordAuthentication(String prompt) {
        statusMessage.setText("Status: Validating...");
        String login = System.getProperty("login");
        if (login == null) {
            return new ValidatorDialog(this, prompt).getPasswordAuthentication();            
        }
        String[] cred = login.split(":");
        return new PasswordAuthentication(cred[0], cred[1].toCharArray());
    }
    
    public void loginFailed(String reason) {
        statusMessage.setText("Status: Login failed (" + reason + ")");
        loginButton.setText("Login");
        loginButton.setActionCommand("login");
        loginButton.setEnabled(true);
    }

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

    public void reconnecting() {
        statusMessage.setText("Status: Reconnecting");
        setSessionButtonsEnabled(false);
    }

    public void reconnected() {
        statusMessage.setText("Status: Reconnected");
        setSessionButtonsEnabled(true);
    }
    
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        if (channel.getName().equals(DCC_CHANNEL_NAME)) {
            dccChannel = channel;
            return this;
        } else {
            ChatChannelFrame cframe = new ChatChannelFrame(this, channel);
            desktop.add(cframe);
            desktop.repaint();
            return cframe;
        }
    }

    private static SessionId getSessionIdAfter(byte[] message, int bytesRead) {
	int sessionIdLen = message.length - bytesRead;
	byte[] sessionIdBytes = new byte[sessionIdLen];
	System.arraycopy(message, 0, sessionIdBytes, 0, sessionIdLen);
	return SessionId.fromBytes(sessionIdBytes);
    }

    public void receivedMessage(byte[] message) {
        if (message.length < 4) {
            System.err.format("ChatClient: Error, short command [%s]\n",
                    new String(message));
        }
	byte[] cmdBytes = new byte[4];
	System.arraycopy(message, 0, cmdBytes, 0, 4);
	String command = new String(cmdBytes);
	System.err.format("ChatClient: Command recv [%s]\n", command);
	if (command.equals("LOGI")) {
	    userLogin(getSessionIdAfter(message, 4));
	} else if (command.equals("LOGO")) {
	    userLogout(getSessionIdAfter(message, 4));
        } else if (command.equals("ECHO")) {
            String msgString = new String(message);
            System.out.println(msgString.substring(4));
	} else {
	    System.err.format("ChatClient: Error, unknown command [%s]\n",
		    command);
	}
    }

    // === ClientChannelListener ===
    
    public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
    {
	JOptionPane.showMessageDialog(this,
		new String(message),
		String.format("Message from %.8s",
			sender.toString()),
			JOptionPane.INFORMATION_MESSAGE);
    }

    public void leftChannel(ClientChannel channel) {
	System.err.format("ChatClient: Error, kicked off channel [%s]\n",
                channel.getName());
    }

    // === ActionListener ===

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

    // === WindowListener ===

    public void windowClosing(WindowEvent e) {
	doQuit();
    }

    public void windowActivated(WindowEvent e)   { /*unused */ }
    public void windowClosed(WindowEvent e)      { /*unused */ }
    public void windowDeactivated(WindowEvent e) { /*unused */ }
    public void windowDeiconified(WindowEvent e) { /*unused */ }
    public void windowIconified(WindowEvent e)   { /*unused */ }
    public void windowOpened(WindowEvent e)      { /*unused */ }

    // === Main ===
    
    public static void main(final String[] args) {
        new ChatClient(args);
    }
}