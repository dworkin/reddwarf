package com.sun.gi.comm.users.server.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.server.UserManager;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;
import com.sun.gi.utils.nio.NIOConnectionListener;
import com.sun.gi.utils.nio.NIOConnection;

public class TCPIPUserManager
	implements NIOSocketManagerListener, UserManager {

    private static Logger log = Logger.getLogger("com.sun.gi.comm.users");

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

	log.info("Starting TCPIP User Manager on host " +
	    host + " port " + port);

	try {
	    socketMgr = new NIOSocketManager();
	    socketMgr.addListener(this);
	    socketMgr.acceptConnectionsOn(host, port);
	} catch (Exception ex) {
	    ex.printStackTrace();
	    throw new InstantiationException(
		"TCPIPUserManager failed to initialize");
	}
    }

    // UserManager methods

    public String getClientClassname() {
	return "com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient";
    }

    public Map<String, String> getClientParams() {
	Map<String, String> params = new HashMap<String, String>();
	params.put("host", host);
	params.put("port", Integer.toString(port));
	return params;
    }

    public void setUserValidatorFactory(UserValidatorFactory factory) {
	validatorFactory = factory;
    }

    // NIOSocketManagerListener methods

    public void newConnection(final NIOConnection connection) {
	log.info("New connection received by server");

	final SGSUserImpl user = new SGSUserImpl(router,
		new TransportProtocolTransmitter() {
	    public void sendBuffers(ByteBuffer[] buffs) {
		sendBuffers(buffs, true);
	    }

	    public void sendBuffers(ByteBuffer[] buffs, boolean reliable) {
		try {
		    log.finer("Server sending opcode: " + buffs[0].get(0));
		    connection.send(buffs, reliable);
		} catch (IOException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
		}
	    }

	    public void closeConnection() {
		log.fine("Server disconnecting user");
		connection.disconnect();
	    }
	}, validatorFactory.newValidators());

	connection.addListener(new NIOConnectionListener() {
	    public void packetReceived(NIOConnection conn,
		    ByteBuffer inputBuffer) {
		log.finer("Server received opcode: " + inputBuffer.get(0));
		user.packetReceived(inputBuffer);
	    }

	    public void disconnected(NIOConnection conn) {
		log.fine("Server sees socket disconnection");
		user.disconnected();
	    }
	});
    }

    /*
     * This callback method is not implemented because a UserManager never
     * initiates a connection.
     *
     * @see  com.sun.gi.utils.nio.NIOSocketManagerListener#connected
     */
    public void connected(NIOConnection connection) {
	throw new UnsupportedOperationException();
    }

    /*
     * This callback method is not implemented because a UserManager never
     * initiates a connection
     *
     * @see  com.sun.gi.utils.nio.NIOSocketManagerListener#connectionFailed
     */
    public void connectionFailed(NIOConnection connection) {
	throw new UnsupportedOperationException();
    }
}
