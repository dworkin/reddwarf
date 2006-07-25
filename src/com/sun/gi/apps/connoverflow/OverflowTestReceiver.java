/**
 * 
 * <p>
 * Title: OverflowTestReceiver.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.apps.connoverflow;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;
import javax.swing.JFrame;

import com.sun.gi.apps.swordworld.client.ValidatorDialog;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

/**
 * 
 * <p>
 * Title: OverflowTestReceiver.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class OverflowTestReceiver extends JFrame
        implements ClientConnectionManagerListener, ClientChannelListener

{
    ClientConnectionManager mgr;

    public OverflowTestReceiver() {
        setSize(100,100);
        setVisible(true);
        try {
            connect("file:discovery.xml");
        } catch (MalformedURLException e) {

            e.printStackTrace();
        } catch (ClientAlreadyConnectedException e) {

            e.printStackTrace();
        }
    }

    public void connect(String discoveryURL) throws MalformedURLException,
            ClientAlreadyConnectedException {
        mgr = new ClientConnectionManagerImpl("OFLOW", new URLDiscoverer(
                new URL(discoveryURL)));
        mgr.setListener(this);
        mgr.connect("com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient");
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        new OverflowTestReceiver();
        while (true)
            ;

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#validationRequest(javax.security.auth.callback.Callback[])
     */
    public void validationRequest(Callback[] callbacks) {
        ValidatorDialog dialog = new ValidatorDialog(this,callbacks);
        mgr.sendValidationResponse(callbacks);          
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#connected(byte[])
     */
    public void connected(byte[] myID) {
        // should start crap coming to us
        // we ARENT going to read it ebcause we WANT a buffer overflow
        // condition on server
        System.out.println("receiver connected");
        mgr.openChannel("junk");

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#connectionRefused(java.lang.String)
     */
    public void connectionRefused(String message) {
        System.out.println("Refused: "+message); 

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#failOverInProgress()
     */
    public void failOverInProgress() {
        System.out.println("failing over");

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#reconnected()
     */
    public void reconnected() {
        System.out.println("Reconnected");

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#disconnected()
     */
    public void disconnected() {
        System.out.println("Disconnected");

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#userJoined(byte[])
     */
    public void userJoined(byte[] userID) {
    // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#userLeft(byte[])
     */
    public void userLeft(byte[] userID) {
    // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#joinedChannel(com.sun.gi.comm.users.client.ClientChannel)
     */
    public void joinedChannel(ClientChannel channel) {
        System.out.println("Joiend channel");
        channel.setListener(this);

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#channelLocked(java.lang.String,
     * byte[])
     */
    public void channelLocked(String channelName, byte[] userID) {
    // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientChannelListener#playerJoined(byte[])
     */
    public void playerJoined(byte[] playerID) {
    // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientChannelListener#playerLeft(byte[])
     */
    public void playerLeft(byte[] playerID) {
    // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientChannelListener#dataArrived(byte[],
     * java.nio.ByteBuffer, boolean)
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        System.out.println("received buffer sized " + data.remaining());
        //now block it up so we can create an overflow
        while(true){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                
                e.printStackTrace();
            }
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.ClientChannelListener#channelClosed()
     */
    public void channelClosed() {
        System.out.println("channel closed");

    }

}
