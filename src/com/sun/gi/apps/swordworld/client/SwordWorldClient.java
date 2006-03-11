/**
 *
 * <p>Title: SwordWorldClient.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.apps.swordworld.client;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;


import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

/**
 *
 * <p>Title: SwordWorldClient.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class SwordWorldClient extends JFrame 
	implements ClientConnectionManagerListener {

	JTextArea outputArea = new JTextArea();
	ClientConnectionManager mgr;
	
	public SwordWorldClient(String discoveryURL) 
		throws MalformedURLException, ClientAlreadyConnectedException{
		super("SwordWorldClient");
		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		final JTextField inputField = new JTextField();
		inputField.addActionListener(new ActionListener(){

			public void actionPerformed(ActionEvent e) {
				sendCommand(inputField.getText());
				
			}
		});
		c.add(inputField,BorderLayout.SOUTH);
		outputArea.setEditable(false);
		c.add(new JScrollPane(outputArea),BorderLayout.CENTER);
		pack();
		setSize(400,400);
		setVisible(true);
		connect(discoveryURL);
	}
		
	// action methods
	/**
	 * @param text
	 */
	protected void sendCommand(String text) {
		ByteBuffer buff = ByteBuffer.allocate(text.length());
		buff.put(text.getBytes());
		mgr.sendToServer(buff,true);
		
	}

	public void connect(String discoveryURL) throws MalformedURLException, ClientAlreadyConnectedException{
		mgr = 
			new ClientConnectionManagerImpl("SwordWorld",
					new URLDiscoverer(
							new URL(discoveryURL)));
		mgr.setListener(this);
		mgr.connect(
				"com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient");
	}
	
	//All the below are methdos defiend by ClientConnectionManagerListener
	
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#validationRequest(javax.security.auth.callback.Callback[])
	 */
	public void validationRequest(Callback[] callbacks) {
		ValidatorDialog dialog = new ValidatorDialog(
				this,callbacks);
		mgr.sendValidationResponse(callbacks);		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#connected(byte[])
	 */
	public void connected(byte[] myID) {
		System.out.println("COnnected");
		mgr.openChannel("GAMECHANNEL");
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#connectionRefused(java.lang.String)
	 */
	public void connectionRefused(String message) {
		System.out.println("Connection failed: "+message);
		System.exit(1);
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#failOverInProgress()
	 */
	public void failOverInProgress() {
		// TODO Auto-generated method stub
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#reconnected()
	 */
	public void reconnected() {
		// TODO Auto-generated method stub
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#disconnected()
	 */
	public void disconnected() {
		System.out.println("Disconnected");
		System.exit(2);
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#userJoined(byte[])
	 */
	public void userJoined(byte[] userID) {
		// TODO Auto-generated method stub
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#userLeft(byte[])
	 */
	public void userLeft(byte[] userID) {
		// TODO Auto-generated method stub
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#joinedChannel(com.sun.gi.comm.users.client.ClientChannel)
	 */
	public void joinedChannel(ClientChannel channel) {
		channel.setListener(new ClientChannelListener(){

			public void playerJoined(byte[] playerID) {
				// TODO Auto-generated method stub
				
			}

			public void playerLeft(byte[] playerID) {
				// TODO Auto-generated method stub
				
			}

			public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
				byte[] inbytes = new byte[data.remaining()];
				data.get(inbytes);
				outputArea.append(new String(inbytes));
				outputArea.append("\n");
				
			}

			public void channelClosed() {
				// TODO Auto-generated method stub
				
			}
			
		});
		
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#channelLocked(java.lang.String, byte[])
	 */
	public void channelLocked(String channelName, byte[] userID) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length==0){
			args = new String[1];
			args[0]="file:FakeDiscovery.xml";
		}
		try {
			new SwordWorldClient(args[0]);
		} catch (MalformedURLException e) {			
			e.printStackTrace();
		} catch (ClientAlreadyConnectedException e) {
			
			e.printStackTrace();
		}

	}
}
