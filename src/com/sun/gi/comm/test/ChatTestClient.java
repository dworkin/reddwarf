package com.sun.gi.comm.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.DefaultListModel;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.types.BYTEARRAY;

@SuppressWarnings("serial")
public class ChatTestClient extends JFrame implements ClientConnectionManagerListener{
	JButton loginButton;
	JButton openChannelButton;
	JLabel statusMessage;
	JDesktopPane desktop;
	JList userList;
	ClientConnectionManager mgr;
	public ChatTestClient(){
		// build interface
		super();
		setTitle("Chat Test Client");
		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		JPanel buttonPanel = new JPanel();
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new GridLayout(0,1));
		statusMessage =new JLabel("Status: Not Connected");
		southPanel.add(buttonPanel);
		southPanel.add(statusMessage);
		c.add(southPanel,BorderLayout.SOUTH);
		desktop = new JDesktopPane();
		c.add(desktop,BorderLayout.CENTER);
		JPanel eastPanel = new JPanel();
		eastPanel.setLayout(new BorderLayout());
		eastPanel.add(new JLabel("Users"),BorderLayout.NORTH);
		userList = new JList();
		eastPanel.add(new JScrollPane(userList),BorderLayout.CENTER);
		c.add(eastPanel,BorderLayout.EAST);
		buttonPanel.setLayout(new GridLayout(1,0));
		loginButton = new JButton("Login");
		loginButton.addActionListener(new ActionListener(){

			public void actionPerformed(ActionEvent e) {
				if (loginButton.getText().equals("Login")){
					loginButton.setEnabled(false);
					String[] classNames = mgr.getUserManagerClassNames();
					String choice = (String)JOptionPane.showInputDialog(ChatTestClient.this,"Choose a user manager","User Manager Selection",
							JOptionPane.INFORMATION_MESSAGE,null,classNames,classNames[0]);
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
				
			}});
		openChannelButton = new JButton("Open Channel");
		buttonPanel.add(loginButton);
		buttonPanel.add(openChannelButton);
		pack();
		setSize(800,600);
		setVisible(true);
		// start connection process.
		try {
			mgr = new ClientConnectionManagerImpl("ChatTest",new URLDiscoverer(new File("FakeDiscovery.xml").toURI().toURL()));
			mgr.setListener(this);
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(2);
		} 
	}
	
	public void validationRequest(Callback[] callbacks) {
		statusMessage.setText("Status: Validating...");
		new ValidatorDialog(this,callbacks);
		mgr.sendValidationResponse(callbacks);		
	}
	
	public void connected(byte[] myID) {
		statusMessage.setText("Status: Connected");
		loginButton.setText("Logout");
		loginButton.setEnabled(true);
		
	}
	public void connectionRefused(String message) {
		statusMessage.setText("Status: Connection refused. ("+message+")");	
		loginButton.setText("Login");
		loginButton.setEnabled(true);
	}
	public void disconnected() {
		statusMessage.setText("Status: logged out");	
		loginButton.setText("Login");
		loginButton.setEnabled(true);		
	}
	
	
	public void userJoined(byte[] userID) {
		DefaultListModel mdl = (DefaultListModel)userList.getModel();
		mdl.addElement(new BYTEARRAY(userID));
		userList.repaint();
		
	}
	public void userLeft(byte[] userID) {
		DefaultListModel mdl = (DefaultListModel)userList.getModel();
		mdl.removeElement(new BYTEARRAY(userID));
		userList.repaint();
	}
	public void joinedChannel(ClientChannel channel) {
		desktop.add(new ChatChannelFrame(channel));
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ChatTestClient();

	}

}
