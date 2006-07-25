/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.swordworld.client;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
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

/**
 */
public class SwordWorldClient extends JFrame 
	implements ClientConnectionManagerListener
{
    private static final long serialVersionUID = 1L;

    JTextArea outputArea = new JTextArea();
    ClientConnectionManager mgr;
    
    public SwordWorldClient(String discoveryURL) 
	throws MalformedURLException, ClientAlreadyConnectedException{
	super("SwordWorldClient");
	Container c = getContentPane();
	c.setLayout(new BorderLayout());
	final JTextField inputField = new JTextField();
	inputField.addActionListener(new ActionListener() {

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
   
    /**
     * Sends a command to the server.
     *
     * @param text the command to send
     */
    protected void sendCommand(String text) {
	ByteBuffer buff = ByteBuffer.allocate(text.length());
	buff.put(text.getBytes());
	mgr.sendToServer(buff,true);
    }

    public void connect(String discoveryURL)
	    throws MalformedURLException, ClientAlreadyConnectedException {
	mgr = new ClientConnectionManagerImpl("SwordWorld",
		new URLDiscoverer(new URL(discoveryURL)));
	mgr.setListener(this);
	mgr.connect("com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient");
    }
    
    //All the below are methods defined by ClientConnectionManagerListener
    
    /** {@inheritDoc} */
    public void validationRequest(Callback[] callbacks) {
	ValidatorDialog dialog = new ValidatorDialog(this,callbacks);
	mgr.sendValidationResponse(callbacks);		
    }

    /** {@inheritDoc} */
    public void connected(byte[] myID) {
	System.out.println("Connected");
	mgr.openChannel("GAMECHANNEL");
    }

    /** {@inheritDoc} */
    public void connectionRefused(String message) {
	System.out.println("Connection failed: " + message);
	System.exit(1);
    }

    /** {@inheritDoc} */
    public void failOverInProgress() {
    }

    /** {@inheritDoc} */
    public void reconnected() {
    }

    /** {@inheritDoc} */
    public void disconnected() {
	System.out.println("Disconnected");
	System.exit(2);
    }

    /** {@inheritDoc} */
    public void userJoined(byte[] userID) {
    }

    /** {@inheritDoc} */
    public void userLeft(byte[] userID) {
    }

    /** {@inheritDoc} */
    public void joinedChannel(ClientChannel channel) {
	channel.setListener(new ClientChannelListener() {

	    /** {@inheritDoc} */
	    public void playerJoined(byte[] playerID) {
	    }

	    /** {@inheritDoc} */
	    public void playerLeft(byte[] playerID) {
	    }

	    /** {@inheritDoc} */
	    public void dataArrived(byte[] from, ByteBuffer data,
		    boolean reliable) {
		byte[] inbytes = new byte[data.remaining()];
		data.get(inbytes);
		outputArea.append(new String(inbytes));
		outputArea.append("\n");
	    }

	    /** {@inheritDoc} */
	    public void channelClosed() {
	    }
	});
    }

    /** {@inheritDoc} */
    public void channelLocked(String channelName, byte[] userID) {
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
	if (args.length == 0){
	    args = new String[1];
	    args[0]="file:discovery.xml";
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
