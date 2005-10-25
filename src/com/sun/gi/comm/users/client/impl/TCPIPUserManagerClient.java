package com.sun.gi.comm.users.client.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.discovery.DiscoveredUserManager;
import com.sun.gi.comm.users.client.UserManagerClient;
import com.sun.gi.comm.users.client.UserManagerClientListener;
import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolClient;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.protocol.impl.BinaryPktProtocol;
import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;
import com.sun.gi.utils.nio.NIOTCPConnection;
import com.sun.gi.utils.nio.NIOTCPConnectionListener;

/**
 * 
 * <p>
 * Title: TCPIPUserManagerClient
 * <p>
 * Description: This class implements a simple TCP/IP based UserManager. It is
 * intended to serve both as a basic UserManager and as an example for the
 * creation of other UserManagers
 * </p>
 * <p>
 * Copyright: Copyright (c) Oct 24, 2005 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems
 * </p>
 * 
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */

public class TCPIPUserManagerClient
        implements UserManagerClient, NIOSocketManagerListener, TransportProtocolClient    {
    NIOSocketManager mgr;
    UserManagerClientListener listener;
    TransportProtocol protocol;
    
    /**
	 * Default constructor
	 * 
	 * @throws InstantiationException
	 */
    public TCPIPUserManagerClient() throws InstantiationException {
        try {
            mgr = new NIOSocketManager();
            mgr.addListener(this);
            protocol = new BinaryPktProtocol();
            protocol.setClient(this);
        } catch (IOException ex) {
            throw new InstantiationException(ex.getMessage());
        }
    }
    
        
    /*
	 * Not implemented because this class does not accept incoming TCP
	 * connections
	 */
	public void newTCPConnection(NIOTCPConnection connection) {
		throw new UnsupportedOperationException();
	}

	public void connected(final NIOTCPConnection connection) {
		protocol.setTransmitter(new TransportProtocolTransmitter(){
			public void sendBuffers(ByteBuffer[] buffs) {
				try {
					connection.send(buffs);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}
		});	
		connection.addListener(new NIOTCPConnectionListener(){
			public void packetReceived(NIOTCPConnection conn, ByteBuffer inputBuffer) {
				protocol.packetReceived(inputBuffer);
				
			}

			public void disconnected(NIOTCPConnection nIOTCPConnection) {
				connectionDropped();
				
			}});
		listener.connected();

	}

	private void connectionDropped() {
		listener.disconnected();		
	}


	public void connectionFailed(NIOTCPConnection connection) {
		listener.disconnected();
		
	}


	public void rcvUnicastMsg(boolean reliable, byte[] chanID, byte[] from, byte[] to, ByteBuffer databuff) {
		listener.recvdData(chanID,from,databuff,reliable);
		
	}


	public void rcvMulticastMsg(boolean reliable, byte[] chanID, byte[] from, byte[][] tolist, ByteBuffer databuff) {
		listener.recvdData(chanID,from,databuff,reliable);
		
	}


	public void rcvBroadcastMsg(boolean reliable, byte[] chanID, byte[] from, ByteBuffer databuff) {
		listener.recvdData(chanID,from,databuff,reliable);
		
	}


	public void rcvValidationReq(Callback[] cbs) {
		listener.validationDataRequest(cbs);
		
	}


	public void rcvUserAccepted(byte[] user) {
		listener.loginAccepted(user);
		
	}


	public void rcvUserRejected(String message) {
		listener.loginRejected(message);
		
	}


	public void rcvUserJoined(byte[] user) {
		listener.userAdded(user);
		
	}


	public void rcvUserLeft(byte[] user) {
		listener.userDropped(user);
		
	}


	public void rcvUserJoinedChan(byte[] chanID, byte[] user) {
		listener.userJoinedChannel(chanID,user);
		
	}


	public void rcvUserLeftChan(byte[] chanID, byte[] user) {
		listener.userLeftChannel(chanID,user);
		
	}


	public void rcvReconnectKey(byte[] user, byte[] key) {
		listener.newConnectionKeyIssued(key);
		
	}


	public void rcvJoinedChan(byte[] chanID) {
		listener.joinedChannel(chanID);
		
	}


	public void rcvLeftChan(byte[] chanID) {
		listener.leftChannel(chanID);
		
	}


	public void connect(DiscoveredUserManager choice, UserManagerClientListener listener) {
		this.listener= listener;
		String host = choice.getParameter("host");
	    int port = Integer.parseInt(choice.getParameter("port"));
	    System.out.println("Attempting to connect to a TCPIP User Manager on host " +
	                host + " port " + port);
	    mgr.makeTCPConnectionTo(host, port);
		
	}


	public void login() {
		protocol.sendLoginRequest();		
	}


	public void validationDataResponse(Callback[] cbs) {
		protocol.sendValidationResponse(cbs);
		
	}


	public void logout() {
		protocol.sendLogoutRequest();		
	}


	public void joinChannel(String channelName) {
		protocol.sendJoinChannelReq(channelName);
		
	}


	public void sendToServer(ByteBuffer buff, boolean reliable) {
		protocol.sendServerMsg(reliable,buff);
		
	}


	public void reconnectLogin(byte[] userID, byte[] reconnectionKey) {
		protocol.sendReconnectRequest(userID,reconnectionKey);
		
	}
    
    
}
