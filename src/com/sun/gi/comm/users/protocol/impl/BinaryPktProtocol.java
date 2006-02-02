package com.sun.gi.comm.users.protocol.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolClient;
import com.sun.gi.comm.users.protocol.TransportProtocolServer;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;

/**
 * Note: buffers coming into the TransportProtocol methods
 * should be in "written" state and full; position == limit.
 */
public class BinaryPktProtocol implements TransportProtocol {

    private static Logger log = Logger.getLogger("com.sun.gi.comm.users");

    private enum OPCODE {
	/** multicast to server */
	SEND_MULTICAST,

	/** multicast from server */
	RECV_MULTICAST,

	/** broadcast to server */
	SEND_BROADCAST,

	/** broadcast from server */
	RECV_BROADCAST,

	/** unicast to server */
	SEND_UNICAST,

	/** unicast from server */
	RECV_UNICAST,

	/** client to GLE */
	SEND_SERVER_MSG,

	/** login request from client */
	CONNECT_REQ,

	/** fail-over login request from client */
	RECONNECT_REQ,

	/** logout request from client */
	DISCONNECT_REQ,

	/** validation challenge from server */
	VALIDATION_REQ,

	/** validation response from client */
	VALIDATION_RESP,

	/** to client: login succeeded */
	USER_ACCEPTED,

	/** to client: login failed */
	USER_REJECTED,

	/** to client: you are logged in */
	USER_JOINED,

	/** to client: you have logged out */
	USER_LEFT,

	/** to client: you are being disconnected */
	USER_DISCONNECTED,

	/** to client: other user joining channel */
	USER_JOINED_CHAN,

	/** to client: other user leaving channel */
	USER_LEFT_CHAN,

	/** to client: reconnect key notification */
	RECV_RECONNECT_KEY,

	/** to server: request to join a channel */
	REQ_JOIN_CHAN,

	/** to client: you have joined a channel */
	JOINED_CHAN,

	/** to server: request to leave a channel */
	REQ_LEAVE_CHAN,

	/** to client: you have left a channel */
	LEFT_CHAN,

	/** to client: the ID from the GLE */
	SERVER_ID,

	/** to client: join/leave channel failed (channel locked) */
	CHAN_LOCKED,
    }


    /** The packet header buffer */
    protected ByteBuffer hdr;

    /**
     * We use two buffers so we can efficiently
     * create the header separately from the payload.
     *
     * sendArray[0] is the header<br />
     * sendArray[1] is the payload
     */
    protected ByteBuffer[] sendArray = new ByteBuffer[2];

    protected TransportProtocolClient	    client;
    protected TransportProtocolServer	    server;
    protected TransportProtocolTransmitter  xmitter;


    /** Default constructor */
    public BinaryPktProtocol() {
	hdr = ByteBuffer.allocate(2048);
	sendArray[0] = hdr;
    }

    public void setClient(TransportProtocolClient client) {
	this.client = client;
    }

    public void setServer(TransportProtocolServer server) {
	this.server = server;
    }

    public void setTransmitter(TransportProtocolTransmitter xmitter) {
	this.xmitter = xmitter;
    }

    public void sendLogoutRequest() throws IOException {
	xmitter.closeConnection();		
    }

    /** Server API: Send server userID to client */
    public void deliverServerID(byte[] server_userid) {
	log.finer("sending server userID to client");
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte) OPCODE.SERVER_ID.ordinal());
	    hdr.put((byte) server_userid.length);
	    hdr.put(server_userid);           
	    sendBuffers(hdr, true);
	}
    }

    /**
     * Client API: user wishes to join a channel
     */
    public synchronized void sendJoinChannelReq(String chanName)
	    throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.REQ_JOIN_CHAN.ordinal());
	    byte[] namebytes = chanName.getBytes();
	    hdr.put((byte) namebytes.length);
	    hdr.put(namebytes);         
	    sendBuffers(hdr, true);
	}
    }

    /**
     * Client API: send a unicast message 
     */
    public synchronized void sendUnicastMsg(byte[] chanID, 
	    byte[] to, boolean reliable, ByteBuffer data) throws IOException {
	log.finer("Unicast send data of size " + data.position());
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte) OPCODE.SEND_UNICAST.ordinal());
	    hdr.put((byte) (reliable ? 1 : 0));
	    hdr.put((byte) chanID.length);
	    hdr.put(chanID);           
	    hdr.put((byte) to.length);
	    hdr.put(to);
	    sendArray[1] = data;
	    sendBuffers(sendArray, reliable);
	}
    }

    /**
     * Server API: deliver a unicast message to the client 
     */
    public synchronized void deliverUnicastMsg(byte[] chanID, byte[] from,
	    byte[] to, boolean reliable, ByteBuffer data) throws IOException {
	log.finer("Unicast recv data of size " + data.position());
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte) OPCODE.RECV_UNICAST.ordinal());
	    hdr.put((byte) (reliable ? 1 : 0));
	    hdr.put((byte) chanID.length);
	    hdr.put(chanID);
	    hdr.put((byte) from.length);
	    hdr.put(from);
	    hdr.put((byte) to.length);
	    hdr.put(to);
	    sendArray[1] = data;
	    sendBuffers(sendArray, reliable);
	}
    }

    /**
     * Client API: send a multicast message 
     */
    public synchronized void sendMulticastMsg(byte[] chanID, byte[][] to,
	    boolean reliable, ByteBuffer data) throws IOException {
	log.finer("Multicast send data of size " + data.position());
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.SEND_MULTICAST.ordinal());
	    hdr.put( (byte) (reliable ? 1 : 0));
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    hdr.put( (byte) to.length);
	    for (int i = 0; i < to.length; i++) {
		hdr.put( (byte) (to[i].length));
		hdr.put(to[i]);
	    }
	    sendArray[1] = data;
	    sendBuffers(sendArray, reliable);
	}
    }

    /**
     * Server API: deliver a multicast message to the client 
     */
    public synchronized void deliverMulticastMsg(byte[] chanID, byte[] from,
	    byte[][] to, boolean reliable, ByteBuffer data)
	    throws IOException {
	log.finer("Multicast recv data of size " + data.position());
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.RECV_MULTICAST.ordinal());
	    hdr.put( (byte) (reliable ? 1 : 0));
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    hdr.put( (byte) from.length);
	    hdr.put(from);
	    hdr.put( (byte) to.length);
	    for (int i = 0; i < to.length; i++) {
		hdr.put( (byte) (to[i].length));
		hdr.put(to[i]);
	    }
	    sendArray[1] = data;
	    sendBuffers(sendArray, reliable);
	}
    }

    /**
     * Client API: send a broadcast message 
     */
    public synchronized void sendBroadcastMsg(byte[] chanID, 
	    boolean reliable, ByteBuffer data) throws IOException {
	log.finer("Broadcast send data of size " + data.position());
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.SEND_BROADCAST.ordinal());
	    hdr.put( (byte) (reliable ? 1 : 0));
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    sendArray[1] = data;
	    sendBuffers(sendArray, reliable);
	}
    }

    /**
     * Server API: deliver a broadcast message to the client 
     */
    public synchronized void deliverBroadcastMsg(byte[] chanID, byte[] from,
	    boolean reliable, ByteBuffer data) throws IOException {
	log.finer("Broadcast recv data of size " + data.position());
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.RECV_BROADCAST.ordinal());
	    hdr.put( (byte) (reliable ? 1 : 0));
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    hdr.put( (byte) from.length);
	    hdr.put(from);
	    sendArray[1] = data;
	    sendBuffers(sendArray, reliable);
	}
    }

    public synchronized void sendServerMsg(boolean reliable,
	    ByteBuffer data) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.SEND_SERVER_MSG.ordinal());
	    hdr.put( (byte) (reliable ? 1 : 0));               
	    sendArray[1] = data;
	    sendBuffers(sendArray, reliable);
	}
    }

    /**
     * Client API: start a login
     */
    public void sendLoginRequest() throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.CONNECT_REQ.ordinal());
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: indcate successful login
     */
    public void deliverUserAccepted( byte[] newID) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.USER_ACCEPTED.ordinal());
	    hdr.put( (byte) newID.length);
	    hdr.put(newID);
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: indcate  login failure
     */
    public void deliverUserRejected(String message) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.USER_REJECTED.ordinal());
	    byte[] msgbytes = message.getBytes();
	    hdr.putInt(msgbytes.length);
	    hdr.put(msgbytes);
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: indcate logout success
     */
    public void deliverUserDisconnected(byte[] userID) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.USER_DISCONNECTED.ordinal());     
	    hdr.put((byte)userID.length);
	    hdr.put(userID);
	    sendBuffers(hdr);
	}
	xmitter.closeConnection();
    }

    /**
     * Client API: attempt a fail-over reconnect
     */
    public void sendReconnectRequest(byte[] from,
	    byte[] reconnectionKey) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.RECONNECT_REQ.ordinal());
	    hdr.put((byte)from.length);
	    hdr.put(from);
	    hdr.put((byte)reconnectionKey.length);
	    hdr.put(reconnectionKey);
	}
    }

    /**
     * Server API: request validation callback information
     */
    public void deliverValidationRequest(Callback[] cbs)
	    throws UnsupportedCallbackException, IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.VALIDATION_REQ.ordinal());
	    ValidationDataProtocol.makeRequestData(hdr, cbs);
	    sendBuffers(hdr);
	}
    }

    /**
     * Client API: send filled-in validation callbacks to the server
     */
    public void sendValidationResponse(Callback[] cbs)
	    throws UnsupportedCallbackException, IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.VALIDATION_RESP.ordinal());
	    ValidationDataProtocol.makeRequestData(hdr, cbs);
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: notify client that some user logged on
     */
    public void deliverUserJoined(byte[] user) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.USER_JOINED.ordinal());
	    hdr.put( (byte) user.length);
	    hdr.put(user);
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: notify client that some user logged off
     */
    public void deliverUserLeft(byte[] user) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.USER_LEFT.ordinal());
	    hdr.put( (byte) user.length);
	    hdr.put(user);
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: notify client that a request to join or leave
     * a channel failed due to the channel being locked.
     *
     * @param channelName  the name of the channel.
     * @param user         the user
     *
     * @throws IOException
     */
    public void deliverChannelLocked(String channelName, byte[] user)
	    throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.CHAN_LOCKED.ordinal());

	    byte[] namebytes = channelName.getBytes();
	    hdr.putInt(namebytes.length);
	    hdr.put(namebytes);

	    hdr.put( (byte) user.length);
	    hdr.put(user);
	    sendBuffers(hdr);
	}
    }


    /**
     * Server API: notify client that some user joined a channel
     */
    public void deliverUserJoinedChannel(byte[] chanID, byte[] user)
	    throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.USER_JOINED_CHAN.ordinal());
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    hdr.put( (byte) user.length);
	    hdr.put(user);
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: notify client that it has joined a channel
     */
    public void deliverJoinedChannel(String channelName, byte[] chanID)
	    throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.JOINED_CHAN.ordinal());
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    byte[] namebytes = channelName.getBytes();
	    hdr.putInt(namebytes.length);
	    hdr.put(namebytes);
	    sendBuffers(hdr);
	}
    }
    /**
      Client API: leave a channel
     */
    public void sendLeaveChannelReq(byte[] chanID) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.REQ_LEAVE_CHAN.ordinal());
	    hdr.put((byte)chanID.length);     
	    hdr.put(chanID);
	    sendBuffers(hdr);
	}
    }

    /**
     * Server API: notify client of some user leaving a channel
     */
    public void deliverUserLeftChannel(byte[] chanID, byte[] user)
	    throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.USER_LEFT_CHAN.ordinal());
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    hdr.put( (byte) user.length);
	    hdr.put(user);
	    sendBuffers(hdr);
	}
    }


    /**
     * Server API: notify client that it has left a channel
     */
    public void deliverLeftChannel(byte[] chanID) throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte)OPCODE.LEFT_CHAN.ordinal());
	    hdr.put((byte)chanID.length);
	    hdr.put(chanID);
	    sendBuffers(hdr);
	}
    }
    /**
     * Server API: send a new reconnection cookie to a clcient
     */
    public void deliverReconnectKey(byte[] id, byte[] key, long ttl)
	    throws IOException {
	synchronized (hdr) {
	    hdr.clear();
	    hdr.put((byte) OPCODE.RECV_RECONNECT_KEY.ordinal());
	    hdr.put((byte) id.length);
	    hdr.put(id);
	    hdr.put((byte) key.length);
	    hdr.put(key);
	    hdr.putLong(ttl);
	    sendBuffers(hdr);
	}
    }

    private void sendBuffers(ByteBuffer buff) {
	sendBuffers(buff, true);
    }

    private void sendBuffers(ByteBuffer buff, boolean reliable) {
	sendBuffers(new ByteBuffer[] { buff }, reliable);
    }

    private void sendBuffers(ByteBuffer[] buffs) {
	sendBuffers(buffs, true);
    }

    private void sendBuffers(ByteBuffer[] buffs, boolean reliable) {
	xmitter.sendBuffers(buffs, reliable);
    }

    // = NIOTCPConnectionListener methods

    /**
     * Determine if the data in <code>buff</code> is a login request
     *
     * @param buff the raw packet
     */
    public boolean isLoginPkt(ByteBuffer buff) {
	int pos = buff.position();
	byte op = buff.get();
	buff.position(pos);
	return op == (byte)OPCODE.CONNECT_REQ.ordinal();
    }

    /**
     * Process raw data recieved from the transport layer
     *
     * @param buff the raw data to process
     */
    public void packetReceived(ByteBuffer buff) {
	OPCODE op = OPCODE.values()[buff.get()];

	log.finer("Recieved op: " + op);
	log.finer("DataSize: " + buff.remaining());

	switch (op) {
	case SEND_UNICAST:
	    boolean reliable = (buff.get() == 1);
	    byte chanIDlen = buff.get();
	    byte[] chanID = new byte[chanIDlen];
	    buff.get(chanID);                 
	    byte tolen = buff.get();
	    byte[] to = new byte[tolen];
	    buff.get(to);
	    ByteBuffer databuff = buff.slice();
	    server.rcvUnicastMsg(reliable, chanID, to, databuff);
	    break;

	case RECV_UNICAST:
	    reliable = (buff.get() == 1);
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);  
	    int fromlen = buff.get();
	    byte[] from = new byte[fromlen];
	    buff.get(from);
	    tolen = buff.get();
	    to = new byte[tolen];
	    buff.get(to);
	    databuff = buff.slice();
	    client.rcvUnicastMsg(reliable, chanID, from, to, databuff);
	    break;    

	case SEND_MULTICAST:
	    reliable = (buff.get() == 1);
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);
	    byte tocount = buff.get();
	    byte[][] tolist = new byte[tocount][];
	    for (int i = 0; i < tocount; i++) {
		tolen = buff.get();
		tolist[i] = new byte[tolen];
		buff.get(tolist[i]);
	    }
	    databuff = buff.slice();
	    server.rcvMulticastMsg(reliable, chanID, tolist, databuff);
	    break;

	case RECV_MULTICAST:
	    reliable = (buff.get() == 1);
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);
	    fromlen = buff.get();
	    from = new byte[fromlen];
	    buff.get(from);
	    tocount = buff.get();
	    tolist = new byte[tocount][];
	    for (int i = 0; i < tocount; i++) {
		tolen = buff.get();
		tolist[i] = new byte[tolen];
		buff.get(tolist[i]);
	    }
	    databuff = buff.slice();
	    client.rcvMulticastMsg(reliable, chanID, from, tolist, databuff);
	    break;    

	case SEND_BROADCAST:
	    reliable = (buff.get() == 1);
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);                
	    databuff = buff.slice();
	    server.rcvBroadcastMsg(reliable, chanID, databuff);
	    break;

	case RECV_BROADCAST:
	    reliable = (buff.get() == 1);
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);
	    fromlen = buff.get();
	    from = new byte[fromlen];
	    buff.get(from);
	    databuff = buff.slice();
	    client.rcvBroadcastMsg(reliable, chanID, from, databuff);
	    break;   

	case SEND_SERVER_MSG:
	    reliable = (buff.get() == 1);                              
	    databuff = buff.slice();
	    server.rcvServerMsg(reliable,databuff);
	    break;

	case RECONNECT_REQ:
	    int usrlen = buff.get();
	    byte[] user = new byte[usrlen];
	    buff.get(user);
	    int keylen = buff.get();
	    byte[] key = new byte[keylen];
	    buff.get(key);
	    server.rcvReconnectReq(user, key);
	    break;

	case CONNECT_REQ:
	    server.rcvConnectReq();
	    break;

	case VALIDATION_REQ:
	    Callback[] cbs = ValidationDataProtocol.unpackRequestData(buff);
	    client.rcvValidationReq(cbs);
	    break;

	case VALIDATION_RESP:
	    cbs = ValidationDataProtocol.unpackRequestData(buff);
	    server.rcvValidationResp(cbs);
	    break;

	case USER_ACCEPTED:
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.rcvUserAccepted(user);
	    break;

	case USER_REJECTED:
	    int bytelen = buff.getInt();
	    byte[] msgbytes = new byte[bytelen];
	    buff.get(msgbytes);
	    client.rcvUserRejected(new String(msgbytes));
	    break;

	case USER_JOINED:
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.rcvUserJoined(user);
	    break;

	case USER_LEFT:
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.rcvUserLeft(user);
	    break;

	case USER_JOINED_CHAN:
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.rcvUserJoinedChan(chanID, user);
	    break;

	case USER_LEFT_CHAN:
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.rcvUserLeftChan(chanID, user);
	    break;

	case JOINED_CHAN:
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);
	    int namelen = buff.getInt();
	    byte[] namebytes = new byte[namelen];
	    buff.get(namebytes);
	    client.rcvJoinedChan(new String(namebytes),chanID);
	    break;

	case LEFT_CHAN:
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);               
	    client.rcvLeftChan(chanID);
	    break;                

	case RECV_RECONNECT_KEY:
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    keylen = buff.get();
	    key = new byte[keylen];
	    buff.get(key);
	    long ttl = buff.getLong();
	    client.rcvReconnectKey(user,key,ttl);
	    break;

	case REQ_JOIN_CHAN:
	    namelen = buff.get();
	    byte[] namearray = new byte[namelen];
	    buff.get(namearray);
	    server.rcvReqJoinChan(new String(namearray));
	    break;

	case REQ_LEAVE_CHAN:               
	    chanIDlen = buff.get();
	    chanID = new byte[chanIDlen];
	    buff.get(chanID);                
	    server.rcvReqLeaveChan(chanID);
	    break; 

	case DISCONNECT_REQ:
	    break;

	case USER_DISCONNECTED:
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.rcvUserDisconnected(user);
	    break;

	case SERVER_ID:
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.recvServerID(user);
	    break;

	case CHAN_LOCKED:
	    namelen = buff.getInt();
	    namebytes = new byte[namelen];
	    buff.get(namebytes);
	    usrlen = buff.get();
	    user = new byte[usrlen];
	    buff.get(user);
	    client.rcvChannelLocked(new String(namebytes), user);
	    break;

	default:
	    log.warning("Invalid op recieved: " + op + "; ignored.");
	    break;
	}
    }
}
