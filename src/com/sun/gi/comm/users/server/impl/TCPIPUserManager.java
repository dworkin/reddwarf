package com.sun.gi.comm.users.server.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.server.UserManager;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;
import com.sun.gi.utils.nio.NIOTCPConnection;
import com.sun.gi.utils.nio.NIOTCPConnectionListener;

public class TCPIPUserManager implements NIOSocketManagerListener, UserManager
	{
	private static final boolean TRACE = true;

	Router router;

	long gameID;

	UserValidatorFactory validatorFactory;

	private String host = "localhost";

	private int port = 1139;

	private NIOSocketManager socketMgr;

	public TCPIPUserManager(Router router, Map params)
			throws InstantiationException {
		this.router = router;
		String p = (String) params.get("host");
		if (p != null) {
			host = p;
		}
		p = (String) params.get("port");
		if (p != null) {
			port = Integer.parseInt(p);
		}
		init();
	}

	private void init() throws InstantiationException {
		System.out.println("Starting TCPIP User Manager on host " + host
				+ " port " + port);
		try {
			socketMgr = new NIOSocketManager();
			socketMgr.addListener(this);
			socketMgr.acceptTCPConnectionsOn(host, port);
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new InstantiationException(
					"TCPIPUserManager failed to initialize");
		}

	}

	/**
	 * getClientClassname
	 * 
	 * @return String
	 */
	public String getClientClassname() {
		return "com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient";
	}

	/**
	 * getClientParams
	 * 
	 * @return Map
	 */
	public Map getClientParams() {
		Map<String, String> params = new HashMap<String, String>();
		params.put("host", host);
		params.put("port", Integer.toString(port));
		return params;
	}

	public void newTCPConnection(final NIOTCPConnection connection) {
		if (TRACE){
			System.out.println("New TCP connection received by server");
		}
		final SGSUserImpl user = new SGSUserImpl(router, new TransportProtocolTransmitter() {
			public void sendBuffers(ByteBuffer[] buffs) {
				try {
					if (TRACE){
						System.out.println("Server sending opcode: "+buffs[0].get(0));
					}
					connection.send(buffs);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}

			public void closeConnection() {
				try {
					if (TRACE){
						System.out.println("Server disconnecting user");
					}
					connection.disconnect();
				} catch (IOException e) {					
					e.printStackTrace();
				}
				
			}			
		}, validatorFactory.newValidators());
		connection.addListener(new NIOTCPConnectionListener(){

			public void packetReceived(NIOTCPConnection conn, ByteBuffer inputBuffer) {
				if (TRACE){
					System.out.println("Server receievd opcode: "+inputBuffer.get(0));
				}
				user.packetReceived(inputBuffer);			
			}

			public void disconnected(NIOTCPConnection nIOTCPConnection) {
				if (TRACE){
					System.out.println("Server sees socket disconnection");
				}
				user.disconnected();					
			}});
		
	}
/*
 *  This callback method is not implemented because a UserManager never initiates a connection
 * @see com.sun.gi.utils.nio.NIOSocketManagerListener#connected(com.sun.gi.utils.nio.NIOTCPConnection)
 */
	public void connected(NIOTCPConnection connection) {
		throw new UnsupportedOperationException();
		
	}
	
	/*
	 *  This callback method is not implemented because a UserManager never initiates a connection
	 * @see com.sun.gi.utils.nio.NIOSocketManagerListener#connectionFailed(com.sun.gi.utils.nio.NIOTCPConnection)
	 */

	public void connectionFailed(NIOTCPConnection connection) {
		throw new UnsupportedOperationException();
		
		
	}

	public void setUserValidatorFactory(UserValidatorFactory validatorFactory) {
		this.validatorFactory = validatorFactory;
		
	}

}
