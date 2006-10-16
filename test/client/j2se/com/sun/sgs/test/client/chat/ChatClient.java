package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import com.sun.sgs.client.ClientLoginListener;
import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ClientLogin;
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
 * 
 * <p>
 * Before connecting to any channels, the user must login to the server
 * via a UserManager. When the Login button is pressed, a list of
 * available UserManagers is displayed. (Right now the TCPIPUserManager
 * is the only implemented option). After selecting a UserManager, the
 * user is prompted for credentials (currently "Guest" with a blank
 * password will do a default login).
 * </p>
 * 
 * <p>
 * Once logged in, the user can open a new channel, or join an existing
 * channel by clicking the "Open Channel" button and typing in the
 * channel name. Once connected to a channel, a separate window opens
 * revealing the users connected to that channel as well as an area to
 * type messages. Each channel opened will appear in a new window.
 * </p>
 * 
 * <p>
 * A user can send a message directly to the server via the "Send to
 * Server" button. Messages are sent to the server via the
 * ClientConnectionManager.
 * </p>
 * 
 * <p>
 * A user can send a Direct Client to Client message via the "Send
 * Multi-DCC" button. This will send a multicast message on the
 * well-known DCC channel.
 * </p>
 * 
 * <p>
 * This class implements ClientConnectionManagerListener so that it can
 * receive and respond to connection events from the server.
 * </p>
 * 
 * <p>
 * The ChatClient is designed to work with the server application,
 * ChatTest. This application must be installed and running on the
 * server for the ChatClient to successfully login. Multiple
 * instances of this program can be run so that multiple users will be
 * shown in the client.
 * </p>
 */
public class ChatClient extends JFrame
        implements ServerSessionListener, ClientLoginListener
{
    private static final long serialVersionUID = 1L;

    JButton loginButton;
    JButton openChannelButton;
    JLabel statusMessage;
    JDesktopPane desktop;

    JList userList; // a list of sessions currently connected to the
                    // ChatApp on the server.

    ClientConnector connector; // used to create a session with the server. 
    ServerSession session;  // used for communication with the server.

    ClientChannel dccChannel; // the well-known channel for Direct
                              // Client to Client communication.

    private JButton dccButton;
    private JButton serverSendButton;
    private static String DCC_CHAN_NAME = "__DCC_Chan";

    public ChatClient(String[] args) {
        // build interface
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
        userList = new JList(new DefaultListModel());
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() > 1) {
                    ClientAddress member = (ClientAddress) userList.getSelectedValue();
                    if (member != null) {
                        String message = JOptionPane.showInputDialog(
                                ChatClient.this, "Enter private message:");
                        doDCCMessage(member, message);
                    }
                }

            }
        });

        userList.setCellRenderer(new ListCellRenderer() {
            JLabel text = new JLabel();

            public Component getListCellRendererComponent(JList arg0,
                    Object arg1, int arg2, boolean arg3, boolean arg4) {
                text.setText(String.format("%.8s", arg1.toString()));
                return text;
            }
        });
        eastPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        c.add(eastPanel, BorderLayout.EAST);
        buttonPanel.setLayout(new GridLayout(1, 0));
        loginButton = new JButton("Login");

        // The login button will attempt to login to the ChatTest
        // application on the server.
        // It must do this via an UserManager. The
        // ClientConnectionManager is used to
        // return an array of valid UserManager class names. Once
        // selected, the ClientConnectionManager
        // will attempt to connect via this UserManager.

        loginButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent action) {
                if (loginButton.getText().equals("Login")) {
                    loginButton.setEnabled(false);
                    // TODO - big time
//                    String[] classNames = connector.getUserManagerClassNames();
//                    String choice = (String) JOptionPane.showInputDialog(
//                            ChatClient.this, "Choose a user manager",
//                            "User Manager Selection",
//                            JOptionPane.INFORMATION_MESSAGE, null, classNames,
//                            classNames[0]);
                    try {
                	connector = ClientConnectorFactory.create(null);
                        connector.connect(ChatClient.this, ChatClient.this);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                } else {
                    session.logout(false);
                }
            }
        });

        // Opens a channel by name on the server. If the channel doesn't
	// exist,
	// it will be opened. In either case, it will attempt to join
	// the user
	// to the specified channel.
        openChannelButton = new JButton("Open Channel");
        openChannelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String channelName = JOptionPane.showInputDialog(
                        ChatClient.this, "Enter channel name");
                openChannel(channelName);
            }
        });
        openChannelButton.setEnabled(false);

        // Sends a message directly to the server.
        serverSendButton = new JButton("Send to Server");
        serverSendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
        	String message = JOptionPane.showInputDialog(
        		ChatClient.this, "Enter server message:");
        	doServerMessage(message);
            }
        });
        serverSendButton.setEnabled(false);

        // sends a mulicast message to the selected users on the DCC
        // channel.
        dccButton = new JButton("Send Multi-DCC ");
        dccButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                ClientAddress[] targets = (ClientAddress[])userList.getSelectedValues();
                if (targets != null) {
                    String message = JOptionPane.showInputDialog(
                            ChatClient.this, "Enter private message:");
                    doMultiDCCMessage(Arrays.asList(targets), message);
                }
            }
        });
        dccButton.setEnabled(false);
        buttonPanel.add(loginButton);
        buttonPanel.add(openChannelButton);
        buttonPanel.add(serverSendButton);
        buttonPanel.add(dccButton);
        pack();
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // start connection process. The ClientConnectionManager is the
        // central point of server communication for the client. The
        // ClientConnectionManager needs to know the application name to
        // attempt to connect to on the server and how to find it. In
        // this case the app name is "ChatTest" and discovery.xml
        // lists the valid UserManagers.
        try {
              // TODO - big time
//            connector = new ClientConnectionManagerImpl("ChatTest",
//                    new URLDiscoverer(
//                            new File(discoveryFileName).toURI().toURL()));
            connector = null; /* new TCPClientConnector(...) */
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

        // When the window closes, disconnect from the manager.
        this.addWindowStateListener(new WindowStateListener() {
            public void windowStateChanged(WindowEvent arg0) {
                if (arg0.getNewState() == WindowEvent.WINDOW_CLOSED) {
                    session.logout(false);
                }
            }
        });
        setVisible(true);
    }

    /**
     * Sends a message to the server via the ClientConnectionManager.
     * 
     * @param message the message to send.
     */
    protected void doServerMessage(String message) {
        session.send(message.getBytes());
    }

    /**
     * Sends a multicast message on the DCC channel.
     * 
     * @param targetList the list of users to send to
     * @param message the message to send
     */
    protected void doMultiDCCMessage(Collection<ClientAddress> targetList, String message) {
        dccChannel.send(targetList, message.getBytes());
    }

    /**
     * Sends a message to a single user on the DCC channel.
     * 
     * @param target the user to send to.
     * @param message the message to send.
     */
    protected void doDCCMessage(ClientAddress target, String message) {
        dccChannel.send(target, message.getBytes());
    }

    public void loginMessageReceived(ClientLogin auth, byte[] message) {
        statusMessage.setText("Status: Validating...");
        new ValidatorDialog(this, auth);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager when the login is
     * successful.
     * 
     * @param myID the user's id. Used for future communication with the
     * server.
     */
    public void connected(ServerSession session) {
	this.session = session;
        statusMessage.setText("Status: Connected");
        setTitle(String.format("Chat Test Client: %.8s", session.toString()));
        loginButton.setText("Logout");
        loginButton.setEnabled(true);
        openChannelButton.setEnabled(true);
        dccButton.setEnabled(true);
        serverSendButton.setEnabled(true);
        openChannel(DCC_CHAN_NAME);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager if the login attempt
     * failed.
     * 
     * @param reason the reason for failure.
     */
    public void loginFailed(String reason) {
        statusMessage.setText("Status: Connection refused. (" + reason + ")");
        loginButton.setText("Login");
        loginButton.setEnabled(true);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by ClientConnectionManager when the user is disconnected.
     * 
     */
    public void disconnected(boolean graceful) {
        openChannelButton.setEnabled(false);
        dccButton.setEnabled(false);
        serverSendButton.setEnabled(false);
        statusMessage.setText("Status: logged out");
        loginButton.setText("Login");
        loginButton.setEnabled(true);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager when a new user joins the
     * application. The new user is added to the user list on the right
     * side.
     * 
     * @param userID the new user
     */
    public void memberLogin(ClientAddress member) {
        DefaultListModel mdl = (DefaultListModel) userList.getModel();
        mdl.addElement(member);
        userList.repaint();

    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager when a user leaves the
     * application. The user is removed from the user list on the right
     * side.
     * 
     * @param userID the user that left.
     */
    public void memberLogout(ClientAddress member) {
        DefaultListModel mdl = (DefaultListModel) userList.getModel();
        mdl.removeElement(member);
        userList.repaint();
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager as confirmation that the
     * user joined the given channel. If the channel is the DCC channel,
     * a ChannelListener will be attached to receive data from it.
     * 
     * @param channel the channel to which the user was joined.
     */
    public void joinedChannel(ClientChannel channel) {
        if (channel.getName().equals(DCC_CHAN_NAME)) {
            dccChannel = channel;
            dccChannel.setListener(new ClientChannelListener() {

		public void receivedMessage(ClientChannel channel, ClientAddress sender, byte[] message)
		{
                    JOptionPane.showMessageDialog(ChatClient.this,
                            new String(message),
                            String.format("Message from %.8s",
                                    sender.toString()),
                            JOptionPane.INFORMATION_MESSAGE);
                }

		public void leftChannel(ClientChannel channel) { }
            });
        } else {
            ChatChannelFrame cframe = new ChatChannelFrame(channel);
            desktop.add(cframe);
            desktop.repaint();
        }
    }

    /**
     * Starting point for the client app.
     * 
     * @param args
     */
    public static void main(String[] args) {
        new ChatClient(args);
    }

    private void openChannel(String channelName) {
	String cmd = "/join " + channelName;
	session.send(cmd.getBytes());
    }

    public void receivedMessage(byte[] contents) {
	String command = new String(contents);
	System.err.format("ChatClient: Command recv [%s]\n", command);
	if (command.startsWith("/login ")) {
	    String memberString = command.substring(7);
	    memberLogin(ClientAddress.fromBytes(ByteBuffer.wrap(memberString.getBytes())));
	} else if (command.startsWith("/logout ")) {
	    String memberString = command.substring(8);
	    memberLogout(ClientAddress.fromBytes(ByteBuffer.wrap(memberString.getBytes())));
	} else {
	    System.err.format("ChatClient: Error, unknown command [%s]\n",
		    command);
	}
    }

    public void reconnecting() {
        statusMessage.setText("Status: Reconnecting");
        openChannelButton.setEnabled(false);
        dccButton.setEnabled(false);
        serverSendButton.setEnabled(false);
    }

    public void reconnected() {
        statusMessage.setText("Status: Reconnected");
        openChannelButton.setEnabled(true);
        dccButton.setEnabled(true);
        serverSendButton.setEnabled(true);
    }

}