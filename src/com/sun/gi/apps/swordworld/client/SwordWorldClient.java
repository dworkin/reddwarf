/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

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
