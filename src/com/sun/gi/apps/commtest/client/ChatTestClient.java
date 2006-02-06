package com.sun.gi.apps.commtest.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.DefaultListModel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.types.BYTEARRAY;
import com.sun.gi.utils.types.StringUtils;

//@SuppressWarnings("serial")
public class ChatTestClient extends JFrame implements
		ClientConnectionManagerListener {
	JButton loginButton;

	JButton openChannelButton;

	JLabel statusMessage;

	JDesktopPane desktop;

	JList userList;

	ClientConnectionManager mgr;

	ClientChannel dccChannel;

	private JButton dccButton;

	private JButton serverSendButton;

	private static String DCC_CHAN_NAME = "__DCC_Chan";

	public ChatTestClient() {
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
		userList
				.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		userList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() > 1) {
					BYTEARRAY ba = (BYTEARRAY) userList.getSelectedValue();
					if (ba != null) {
						String message = JOptionPane.showInputDialog(
								ChatTestClient.this,
								"Enter private message:");
						doDCCMessage(ba.data(), message);
					}
				}

			}
		});
		userList.setCellRenderer(new ListCellRenderer(){
			JLabel text = new JLabel();
			public Component getListCellRendererComponent(JList arg0, Object arg1, int arg2, boolean arg3, boolean arg4) {
				byte[] data = ((BYTEARRAY)arg1).data();
				text.setText(StringUtils.bytesToHex(data,data.length-4));
				return text;
			}});
		eastPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
		c.add(eastPanel, BorderLayout.EAST);
		buttonPanel.setLayout(new GridLayout(1, 0));
		loginButton = new JButton("Login");
		loginButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (loginButton.getText().equals("Login")) {
					loginButton.setEnabled(false);
					String[] classNames = mgr.getUserManagerClassNames();
					String choice = (String) JOptionPane.showInputDialog(
							ChatTestClient.this, "Choose a user manager",
							"User Manager Selection",
							JOptionPane.INFORMATION_MESSAGE, null, classNames,
							classNames[0]);
					try {
						mgr.connect(choice);
					} catch (ClientAlreadyConnectedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
						System.exit(1);
					}
				} else {
					mgr.disconnect();
				}

			}
		});
		openChannelButton = new JButton("Open Channel");
		openChannelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String channelName = JOptionPane.showInputDialog(
						ChatTestClient.this, "Enter channel name");
				mgr.openChannel(channelName);
			}
		});
		openChannelButton.setEnabled(false);
		serverSendButton = new JButton("Send to Server");
		serverSendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Object[] targets = userList.getSelectedValues();
				if (targets != null) {
					String message = JOptionPane.showInputDialog(
							ChatTestClient.this,
							"Enter server message:");					
					doServerMessage(message);
				}
			}
		});
		serverSendButton.setEnabled(false);
		dccButton = new JButton("Send Multi-DCC ");
		dccButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Object[] targets = userList.getSelectedValues();
				if (targets != null) {
					String message = JOptionPane.showInputDialog(
							ChatTestClient.this,
							"Enter private message:");
					byte[][] targetList = new byte[targets.length][];
					for(int i=0;i<targets.length;i++){
						targetList[i] = ((BYTEARRAY)targets[i]).data();
					}
					doMultiDCCMessage(targetList,message);
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
		// start connection process.
		try {
			mgr = new ClientConnectionManagerImpl("ChatTest",
					new URLDiscoverer(new File("FakeDiscovery.xml").toURI()
							.toURL()));
			mgr.setListener(this);

		} catch (MalformedURLException e) {
			e.printStackTrace();
			System.exit(2);
		}
		this.addWindowStateListener(new WindowStateListener() {
			public void windowStateChanged(WindowEvent arg0) {
				if (arg0.getNewState() == WindowEvent.WINDOW_CLOSED) {
					mgr.disconnect();
				}
			}
		});
		setVisible(true);
	}

	/**
	 * @param message
	 */
	protected void doServerMessage(String message) {
		ByteBuffer out = ByteBuffer.allocate(message.length());
		out.put(message.getBytes());
		mgr.sendToServer(out,true);
		
	}

	/**
	 * @param targetList
	 * @param message
	 */
	protected void doMultiDCCMessage(byte[][] targetList, String message) {
		ByteBuffer out = ByteBuffer.allocate(message.length());
		out.put(message.getBytes());
		dccChannel.sendMulticastData(targetList, out, true);
		
	}

	/**
	 * 
	 */
	protected void doDCCMessage(byte[] target, String message) {
		ByteBuffer out = ByteBuffer.allocate(message.length());
		out.put(message.getBytes());
		dccChannel.sendUnicastData(target, out, true);

	}

	public void validationRequest(Callback[] callbacks) {
		statusMessage.setText("Status: Validating...");
		new ValidatorDialog(this, callbacks);
		mgr.sendValidationResponse(callbacks);
	}

	public void connected(byte[] myID) {
		statusMessage.setText("Status: Connected");
		setTitle("Chat Test Client: " + StringUtils.bytesToHex(myID));
		loginButton.setText("Logout");
		loginButton.setEnabled(true);
		openChannelButton.setEnabled(true);
		dccButton.setEnabled(true);
		serverSendButton.setEnabled(true);
		mgr.openChannel(DCC_CHAN_NAME);
	}

	public void connectionRefused(String message) {
		statusMessage.setText("Status: Connection refused. (" + message + ")");
		loginButton.setText("Login");
		loginButton.setEnabled(true);
	}

	public void disconnected() {
		statusMessage.setText("Status: logged out");
		loginButton.setText("Login");
		loginButton.setEnabled(true);
		openChannelButton.setEnabled(false);
		dccButton.setEnabled(false);
		serverSendButton.setEnabled(false);
	}

	public void userJoined(byte[] userID) {
		DefaultListModel mdl = (DefaultListModel) userList.getModel();
		mdl.addElement(new BYTEARRAY(userID));
		userList.repaint();

	}

	public void userLeft(byte[] userID) {
		DefaultListModel mdl = (DefaultListModel) userList.getModel();
		mdl.removeElement(new BYTEARRAY(userID));
		userList.repaint();
	}

	public void joinedChannel(ClientChannel channel) {
		if (channel.getName().equals(DCC_CHAN_NAME)) {
			dccChannel = channel;
			dccChannel.setListener(new ClientChannelListener() {

				public void playerJoined(byte[] playerID) {
					// TODO Auto-generated method stub

				}

				public void playerLeft(byte[] playerID) {
					// TODO Auto-generated method stub

				}

				public void dataArrived(byte[] from, ByteBuffer data,
						boolean reliable) {
					byte[] bytes = new byte[data.remaining()];
					data.get(bytes);
					JOptionPane.showMessageDialog(ChatTestClient.this,
							new String(bytes), "Message from "
									+ StringUtils.bytesToHex(from,
											from.length - 4),
							JOptionPane.INFORMATION_MESSAGE);

				}

				public void channelClosed() {
					// TODO Auto-generated method stub

				}
			});
		} else {
			ChatChannelFrame cframe = new ChatChannelFrame(channel);
			desktop.add(cframe);
			desktop.repaint();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ChatTestClient();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#failOverInProgress()
	 */
	public void failOverInProgress() {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#reconnected()
	 */
	public void reconnected() {
		// TODO Auto-generated method stub

	}
	
	/**
	 * This method is called whenever an attempted join/leave fails due to 
	 * the target channel being locked.
	 * 
	 * @param channelName		the name of the channel.
	 * @param userID			the ID of the user attemping to join/leave
	 */
	public void channelLocked(String channelName, byte[] userID) {
		System.out.println("ChatTestClient received locked notification: " + channelName);
	}

}
