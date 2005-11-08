package com.sun.gi.comm.users.protocol.impl;

import java.io.*;
import java.nio.*;
import java.util.*;
import javax.security.auth.callback.*;


import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolClient;

import com.sun.gi.comm.users.protocol.TransportProtocolServer;

import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;





public class BinaryPktProtocol 
        implements TransportProtocol {
	
	private enum OPCODE {	SEND_MULTICAST, // client to server multicast msg
							RCV_MULTICAST, // serv to client multicast msg
							SEND_BROADCAST, // client to server broadcast msg
							RCV_BROADCAST,  // serv to client broadcast msg
							SEND_UNICAST, // client to server unicast msg
							RCV_UNICAST, // server to client unicast msg
							SEND_SERVER_MSG, // client to GLE
							CONNECT_REQ, // client to server login
							RECONNECT_REQ, //client to server fail-over login
							DISCONNECT_REQ, //client to server logout request
							VALIDATION_REQ, // server to client req for validation cb data
							VALIDATION_RESP, // client to server validation cb data
							USER_ACCEPTED, // server to client successful login
							USER_REJECTED, //server to client failed login
							USER_JOINED, // server to client notification of user logging in
							USER_LEFT, //server to client notification of user logging out
							USER_DISCONNECTED, //this user is being disconnected
							USER_JOINED_CHAN, // server to client notification of other user joining
							USER_LEFT_CHAN, //server to client notification of other user leaving channel
							RCV_RECONNECT_KEY, //server to client reconnect key notification
							REQ_JOIN_CHAN,  // client to server request to join a channel
							JOINED_CHAN, // Server to client notification of user joining a channel
							REQ_LEAVE_CHAN, // client to server req to leave a channel
							LEFT_CHAN // server to client notification of user leaving a channel
	}
	
    private ByteBuffer hdr;
    private ByteBuffer[] sendArray = new ByteBuffer[2];
    private static final boolean TRACEON = false;
    private TransportProtocolClient client;
    private TransportProtocolServer server;
    private TransportProtocolTransmitter xmitter;
    
    /**
     * This is the default constructor
     *
     */
    
    public BinaryPktProtocol(){
        hdr = ByteBuffer.allocate(2048);
        sendArray[0] = hdr;
        
    }
    
    
    /**
     * Call this method from the client to indicate that the user wishes to join a channel
     */
    
    public synchronized void sendJoinChannelReq(String chanName) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.REQ_JOIN_CHAN.ordinal());
            byte[] namebytes = chanName.getBytes();
            hdr.put((byte) namebytes.length);
            hdr.put(namebytes);         
            sendBuffers(hdr);
        }
    }
    
   
    
     /**
      * Call this method from the client to send a unicast message 
      */
     
    public synchronized void sendUnicastMsg(byte[] chanID, 
            byte[] to,
            boolean reliable, ByteBuffer data) throws
            IOException {
        if (TRACEON){
            System.out.println("Unicast Sending data of size: " + data.position());
        }
        System.out.flush();
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.SEND_UNICAST.ordinal());
            hdr.put( (byte) (reliable ? 1 : 0));
            hdr.put((byte) chanID.length);
            hdr.put(chanID);           
            hdr.put( (byte) to.length);
            hdr.put(to);
            sendArray[1] = data;
            sendBuffers(sendArray);
        }
    }
    
    /**
     * Call this method from the server to deliver a unicast message to the client 
     */
    
    public synchronized void deliverUnicastMsg(byte[] chanID, byte[] from,
            byte[] to,
            boolean reliable, ByteBuffer data) throws
            IOException {
        if (TRACEON){
            System.out.println("Unicast Sending data of size: " + data.position());
        }
        System.out.flush();
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.RCV_UNICAST.ordinal());
            hdr.put( (byte) (reliable ? 1 : 0));
            hdr.put((byte) chanID.length);
            hdr.put(chanID);
            hdr.put( (byte) from.length);
            hdr.put(from);
            hdr.put( (byte) to.length);
            hdr.put(to);
            sendArray[1] = data;
            sendBuffers(sendArray);
        }
    }
    
    /**
     * Call this method from the client to send a multicast message 
     */
    
    public synchronized void sendMulticastMsg(byte[] chanID, 
            byte[][] to,
            boolean reliable,
            ByteBuffer data) throws IOException {
        if (TRACEON){
            System.out.println("Multicast Sending data of size: " + data.position());
        }
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
            sendBuffers(sendArray);
        }
    }
    
    /**
     * Call this method from the server to deliver a multicast message to the client 
     */
    
    public synchronized void deliverMulticastMsg(byte[] chanID, byte[] from,
            byte[][] to,
            boolean reliable,
            ByteBuffer data) throws IOException {
        if (TRACEON){
            System.out.println("Multicast Sending data of size: " + data.position());
        }
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.RCV_MULTICAST.ordinal());
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
            sendBuffers(sendArray);
        }
    }
    
    /**
     * Call this method from the client to send a multicast message 
     */
    
    public synchronized void sendBroadcastMsg(byte[] chanID, 
            boolean reliable,
            ByteBuffer data) throws IOException {
        // buffers coming into here should be in "written" state and full
        // position == limit
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.SEND_BROADCAST.ordinal());
            hdr.put( (byte) (reliable ? 1 : 0));
            hdr.put((byte)chanID.length);
            hdr.put(chanID);
            sendArray[1] = data;
            sendBuffers(sendArray);
        }
    }
    
    /**
     * Call this method from the server to deliver a multicast message to the client 
     */
    
    public synchronized void deliverBroadcastMsg(byte[] chanID, byte[] from,
            boolean reliable,
            ByteBuffer data) throws IOException {
        // buffers coming into here should be in "written" state and full
        // position == limit
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.RCV_BROADCAST.ordinal());
            hdr.put( (byte) (reliable ? 1 : 0));
            hdr.put((byte)chanID.length);
            hdr.put(chanID);
            hdr.put( (byte) from.length);
            hdr.put(from);
            sendArray[1] = data;
            sendBuffers(sendArray);
        }
    }
    
    public synchronized void sendServerMsg(
            boolean reliable,
            ByteBuffer data) throws IOException {
        // buffers coming into here should be in "written" state and full
        // position == limit
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.SEND_SERVER_MSG.ordinal());
            hdr.put( (byte) (reliable ? 1 : 0));                       
            sendArray[1] = data;
            sendBuffers(sendArray);
        }
    }
    
    /**
     * Call this method from the client to start a login
     */
    
    public void sendLoginRequest() throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.CONNECT_REQ.ordinal());
            sendBuffers(hdr);
        }
    }
    
    /**
     * Call this method from the server to indcate successful login
     */
    public void deliverUserAccepted( byte[] newID) throws
            IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.USER_ACCEPTED.ordinal());
            hdr.put( (byte) newID.length);
            hdr.put(newID);
            sendBuffers(hdr);
        }
    }
    
    /**
     * Call this method from the server to indcate  login failure
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
     * Call this method from the server to indcate logout success
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
     * Call this method from the client to attempt a fail-over reconnect
     */
    
    public void sendReconnectRequest(byte[] from,
            byte[] reconnectionKey) throws
            IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.RECONNECT_REQ.ordinal());
            hdr.put((byte)from.length);
            hdr.put(from);
            hdr.put((byte)reconnectionKey.length);
            hdr.put(reconnectionKey);
            sendBuffers(hdr);
        }
    }
    
    /**
     * Call this method from the server to request validation callback information
     */
    
    public void deliverValidationRequest(Callback[] cbs) throws
            UnsupportedCallbackException, IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.VALIDATION_REQ.ordinal());
            ValidationDataProtocol.makeRequestData(hdr, cbs);
            sendBuffers(hdr);
        }
    }
    
    /**
     * Call this method from the client to send fileld out validation callbacks to the server
     */
    
    public void sendValidationResponse(Callback[] cbs) throws
            UnsupportedCallbackException, IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.VALIDATION_RESP.ordinal());
            ValidationDataProtocol.makeRequestData(hdr, cbs);
            sendBuffers(hdr);
        }
    }
    
    /**
     * Call this method from the server to notify client of newly logged on user
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
     * Call this method from the server to notify client of newly logged off user
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
     * Call this method from the server to notify client of user joining channel
     */
    
    public void deliverUserJoinedChannel(byte[] chanID, byte[] user) throws IOException {
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
     * Call this method from the server to notify client of itself joining channel
     */
    
    public void deliverJoinedChannel(byte[] chanID) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.JOINED_CHAN.ordinal());
            hdr.put((byte)chanID.length);
            hdr.put(chanID);
            sendBuffers(hdr);
        }
    }
    /**
     * Call this method from the client to leave a channel
     */
    
    public void sendLeaveChannelReq(byte[] chanID) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.REQ_LEAVE_CHAN.ordinal());
            hdr.put((byte)chanID.length);            
            sendBuffers(hdr);
        }
    }
    
    /**
     * Call this method from the server to notify client of user leaving channel
     */
    
    public void deliverUserLeftChannel(byte[] chanID, byte[] user) throws IOException {
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
     * Call this method from the server to notify client of itself leaving channel
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
     * call this method from the server to send a reconenct key update to the client
     */
    public void deliverReconnectKey(byte[] id, byte[] key) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte)OPCODE.RCV_RECONNECT_KEY.ordinal());
            hdr.put((byte)id.length);
            hdr.put(id);
            hdr.put( (byte) key.length);
            hdr.put(key);
            sendBuffers(hdr);
        }
    }
    
    private void sendBuffers(ByteBuffer buff){
    	xmitter.sendBuffers(new ByteBuffer[] {buff});
    }
    
    private void sendBuffers(ByteBuffer[] buffs){
    	xmitter.sendBuffers(buffs);
    }
    
    //NIOTCPConnectionListener
    
    
    public boolean isLoginPkt(ByteBuffer buff){
    	int pos = buff.position();
    	byte op = buff.get();
    	buff.position(pos);
    	return op == (byte)OPCODE.CONNECT_REQ.ordinal();
    }
    
    /**
     * packetReceived
     *
     * @param conn NIOTCPConnection
     * @param inputBuffer ByteBuffer
     */
    public void packetReceived(ByteBuffer buff) {
        OPCODE op = OPCODE.values()[buff.get()];
        if (TRACEON){
            System.out.println("Recieved op: " + op);
            System.out.println("DataSize: " + buff.remaining());
        }
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
            case RCV_UNICAST:
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
                server.rcvMulticastMsg(reliable, chanID,  tolist, databuff);
                break;
            case RCV_MULTICAST:
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
                server.rcvBroadcastMsg(reliable, chanID,  databuff);
                break;
            case RCV_BROADCAST:
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
                fromlen = buff.get();
                from = new byte[fromlen];
                buff.get(from);
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
                client.rcvJoinedChan(chanID);
                break;
            case LEFT_CHAN:
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);               
                client.rcvLeftChan(chanID);
                break;                
            case RCV_RECONNECT_KEY:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                keylen = buff.get();
                key = new byte[keylen];
                buff.get(key);
                client.rcvReconnectKey(user,key);
                break;
            case REQ_JOIN_CHAN:
            	int namelen = buff.get();
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
            default:
                System.out.println("WARNING:Invalid op recieved from client: " + op +
                        " ignored.");
                break;
        }
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
		// TODO Auto-generated method stub
		
	}





    
    
}
