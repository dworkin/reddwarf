package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.nio.ByteBuffer;
import java.util.Collection;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ClientConnector;
import com.sun.sgs.client.ClientAddress;
import com.sun.sgs.client.ClientConnectorFactory;
import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.ServerSessionListener;

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
        	ServerSessionListener, ClientChannelListener
{
    private static final long serialVersionUID = 1L;

    private final JButton loginButton;
    private final JButton openChannelButton;
    private final JButton multiDccButton;
    private final JButton serverSendButton;
    private final JLabel statusMessage;
    private final JDesktopPane desktop;

    private static final String DCC_CHANNEL_NAME = "__DCC_Chan";

    private final MemberList userList;	// a list of sessions currently connected to the
    			// ChatApp on the server.

    private ClientConnector connector; // used to create a session with the server. 
    private ServerSession session;     // used for communication with the server.

    private ClientChannel dccChannel;	// the well-known channel for Direct
				// Client to Client communication.


    private int quitAttempts = 0;

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
            connector = ClientConnectorFactory.createConnector(null);
            connector.connect(ChatClient.this);
            // TODO: enable the loginButton as a "Cancel Login" action.
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void doLogout() {
	setButtonsEnabled(false);
        session.logout(false);
    }

    private void doQuit() {
	++quitAttempts;
	switch (quitAttempts) {
	case 1:
	    session.logout(false);
	    break;
	case 2:
	    session.logout(true);
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
        session.send(ByteBuffer.wrap(message.getBytes()));
    }

    private void doMultiDCCMessage() {
	Collection<ClientAddress> targets = userList.getSelectedClients();
    	if (targets == null || targets.isEmpty()) {
    	    return;
    	}

        String message = getUserInput("Enter private message:");
        dccChannel.send(targets, ByteBuffer.wrap(message.getBytes()));
    }

    void doDCCMessage() {
	ClientAddress target = userList.getSelectedClient();
	if (target == null) {
	    return;
	}
	String message = getUserInput("Enter private message:");
        dccChannel.send(target, ByteBuffer.wrap(message.getBytes()));
    }

    void joinChannel(String channelName) {
	String cmd = "JOIN" + channelName;
	session.send(ByteBuffer.wrap(cmd.getBytes()));
    }
 
    void leaveChannel(ClientChannel chan) {
	String cmd = "LEAV" + chan.getName();
	session.send(ByteBuffer.wrap(cmd.getBytes()));
    }

    private void userLogin(ClientAddress member) {
        userList.addClient(member);
    }

    private void userLogout(ClientAddress member) {
        userList.removeClient(member);
    }

    public String getCredentials() {
        statusMessage.setText("Status: Validating...");
        // TODO
        //return new ValidatorDialog(this);
        return null;
    }
    
    // === ServerSessionListener ===

    public void connected(ServerSession session) {
	this.session = session;
        statusMessage.setText("Status: Connected");
        setTitle(String.format("Chat Test Client: %.8s", session.toString()));
        loginButton.setText("Logout");
        loginButton.setActionCommand("logout");
        setButtonsEnabled(true);
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
            this.dispose();
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

    public void receivedMessage(ByteBuffer message) {
	byte[] messageBytes = new byte[4];
	message.get(messageBytes);
	String command = new String(messageBytes);
	System.err.format("ChatClient: Command recv [%s]\n", command);
	if (command.equals("LOGI")) {
	    userLogin(ClientAddress.fromBytes(message));
	} else if (command.equals("LOGO")) {
	    userLogout(ClientAddress.fromBytes(message));
	} else {
	    System.err.format("ChatClient: Error, unknown command [%s]\n",
		    command);
	}
    }

    // === ClientChannelListener ===
    
    public void receivedMessage(ClientChannel channel, ClientAddress sender, ByteBuffer message) {
	byte[] messageBytes = new byte[message.remaining()];
	message.get(messageBytes);
	JOptionPane.showMessageDialog(this,
		new String(messageBytes),
		String.format("Message from %.8s",
			sender.toString()),
			JOptionPane.INFORMATION_MESSAGE);
    }

    public void leftChannel(ClientChannel channel) {
	System.err.format("ChatClient: Error, kicked off channel [%s]\n", channel.getName());
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
            System.err.format("ChatClient: Error, unknown GUI command [%s]\n", command);
        }
    }

    // === WindowListener ===

    public void windowClosing(WindowEvent e) {
	doQuit();
    }

    public void windowActivated(WindowEvent e) { }
    public void windowClosed(WindowEvent e) { }
    public void windowDeactivated(WindowEvent e) { }
    public void windowDeiconified(WindowEvent e) { }
    public void windowIconified(WindowEvent e) { }
    public void windowOpened(WindowEvent e) { }

    // === Main ===
    
    public static void main(String[] args) {
        new ChatClient(args);
    }
}