/**
 * 
 * <p>
 * Title: OverflowTestClient.java
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

import com.sun.gi.apps.swordworld.client.ValidatorDialog;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

/**
 * 
 * <p>
 * Title: OverflowTestClient.java
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
public class OverflowTestSender implements ClientConnectionManagerListener,
        ClientChannelListener
 
{
    ClientConnectionManager mgr;
    
    public OverflowTestSender(){
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
        new OverflowTestSender();
        while(true);

    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#validationRequest(javax.security.auth.callback.Callback[])
     */
    public void validationRequest(Callback[] callbacks) {
        ValidatorDialog dialog = new ValidatorDialog(null,callbacks);
        mgr.sendValidationResponse(callbacks);          
    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#connected(byte[])
     */
    public void connected(byte[] myID) {
        //should start crap coming to us
        //we ARENT going to read it ebcause we WANT a buffer overflow condition on server
        System.out.println("Sender connected");
        mgr.openChannel("junk");
        
    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#connectionRefused(java.lang.String)
     */
    public void connectionRefused(String message) {
        // TODO Auto-generated method stub
        
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
        // TODO Auto-generated method stub
        
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
        System.out.println("channel joined");
        channel.setListener(this);
        byte[] bytes = new byte[1000];
        final ByteBuffer buff = ByteBuffer.allocate(1000);
        buff.put(bytes); 
        final ClientChannel chan = channel;
        new Thread(new Runnable(){
            public void run (){
                while(true){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        
                        e.printStackTrace();
                    }
                    chan.sendBroadcastData(buff.duplicate(),true);
                }
            }
        }).start();    
        
        
    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientConnectionManagerListener#channelLocked(java.lang.String, byte[])
     */
    public void channelLocked(String channelName, byte[] userID) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientChannelListener#playerJoined(byte[])
     */
    public void playerJoined(byte[] playerID) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientChannelListener#playerLeft(byte[])
     */
    public void playerLeft(byte[] playerID) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientChannelListener#dataArrived(byte[], java.nio.ByteBuffer, boolean)
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
       
    
        
    }

    /* (non-Javadoc)
     * @see com.sun.gi.comm.users.client.ClientChannelListener#channelClosed()
     */
    public void channelClosed() {
        // TODO Auto-generated method stub
        
    }

}
