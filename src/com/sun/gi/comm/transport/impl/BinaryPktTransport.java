package com.sun.gi.comm.transport.impl;

import java.io.*;
import java.nio.*;
import java.util.*;
import javax.security.auth.callback.*;

import com.sun.gi.comm.transport.*;
import com.sun.gi.comm.validation.*;
import com.sun.gi.utils.nio.*;

public class BinaryPktTransport
        implements Transport, NIOTCPConnectionListener {
    private static final byte OP_UNICAST_MSG = 1;
    private static final byte OP_MULTICAST_MSG = 2;
    private static final byte OP_BROADCAST_MSG = 3;
    private static final byte OP_CONNECT_REQ = 4;
    private static final byte OP_RECONNECT_REQ = 5;
    private static final byte OP_VALIDATION_REQ = 6;
    private static final byte OP_VALIDATION_RESP = 7;
    private static final byte OP_USER_ACCEPTED = 8;
    private static final byte OP_USER_REJECTED = 9;
    private static final byte OP_USER_JOINED = 10;
    private static final byte OP_USER_LEFT = 11;
    private static final byte OP_RECONNECT_KEY = 12;
    private static final byte OP_USER_JOINED_CHAN = 13;
    private static final byte OP_USER_LEFT_CHAN = 14;
    private static final byte OP_REQ_JOIN_CHAN = 15;
    private static final byte OP_JOINED_CHAN = 16;
    
    
    private ByteBuffer hdr;
    private ByteBuffer[] sendArray = new ByteBuffer[2];
    private List listeners = new ArrayList();
    private static final boolean TRACEON = false;
    
    public BinaryPktTransport(){
        hdr = ByteBuffer.allocate(2048);
        sendArray[0] = hdr;
        
    }
    
    public void addListener(TransportListener l) {
        listeners.add(l);
    }
    
    public synchronized void sendUserJoinChan(String chanName, byte[] userID) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_REQ_JOIN_CHAN);
            byte[] namebytes = chanName.getBytes();
            hdr.put((byte) namebytes.length);
            hdr.put(namebytes);
            hdr.put( (byte) userID.length);
            hdr.put(userID);
            fireSendBuffers(hdr);
        }
    }
    
     public synchronized void sendJoinedChan(String chanName, byte[] chanID) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_REQ_JOIN_CHAN);
            byte[] namebytes = chanName.getBytes();
            hdr.put((byte) namebytes.length);
            hdr.put(namebytes);
            hdr.put( (byte) chanID.length);
            hdr.put(chanID);
            fireSendBuffers(hdr);
        }
    }
    
    public synchronized void sendUnicastMsg(byte[] chanID, byte[] from,
            byte[] to,
            boolean reliable, ByteBuffer data) throws
            IOException {
        if (TRACEON){
            System.out.println("Unicast Sending data of size: " + data.position());
        }
        System.out.flush();
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_UNICAST_MSG);
            hdr.put( (byte) (reliable ? 1 : 0));
            hdr.put((byte) chanID.length);
            hdr.put(chanID);
            hdr.put( (byte) from.length);
            hdr.put(from);
            hdr.put( (byte) to.length);
            hdr.put(to);
            sendArray[1] = data;
            fireSendBuffers(sendArray);
        }
    }
    
    public synchronized void sendMulticastMsg(byte[] chanID, byte[] from,
            byte[][] to,
            boolean reliable,
            ByteBuffer data) throws IOException {
        if (TRACEON){
            System.out.println("Multicast Sending data of size: " + data.position());
        }
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_MULTICAST_MSG);
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
            fireSendBuffers(sendArray);
        }
    }
    
    public synchronized void sendBroadcastMsg(byte[] chanID, byte[] from,
            boolean reliable,
            ByteBuffer data) throws IOException {
        // buffers coming into here should be in "written" state and full
        // position == limit
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_BROADCAST_MSG);
            hdr.put( (byte) (reliable ? 1 : 0));
            hdr.put((byte)chanID.length);
            hdr.put(chanID);
            hdr.put( (byte) from.length);
            hdr.put(from);
            sendArray[1] = data;
            fireSendBuffers(sendArray);
        }
    }
    
    public void sendConnectionRequest() throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_CONNECT_REQ);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendUserAccepted( byte[] newID) throws
            IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_USER_ACCEPTED);
            hdr.put( (byte) newID.length);
            hdr.put(newID);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendUserRejected(String message) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_USER_REJECTED);
            byte[] msgbytes = message.getBytes();
            hdr.putInt(msgbytes.length);
            hdr.put(msgbytes);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendReconnectRequest(byte[] from,
            byte[] reconnectionKey) throws
            IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_RECONNECT_REQ);
            hdr.put((byte)from.length);
            hdr.put(from);
            hdr.put((byte)reconnectionKey.length);
            hdr.put(reconnectionKey);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendValidationRequest(Callback[] cbs) throws
            UnsupportedCallbackException, IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_VALIDATION_REQ);
            ValidationDataProtocol.makeRequestData(hdr, cbs);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendValidationResponse(Callback[] cbs) throws
            UnsupportedCallbackException, IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_VALIDATION_RESP);
            ValidationDataProtocol.makeRequestData(hdr, cbs);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendUserJoined(byte[] user) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_USER_JOINED);
            hdr.put( (byte) user.length);
            hdr.put(user);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendUserLeft(byte[] user) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_USER_LEFT);
            hdr.put( (byte) user.length);
            hdr.put(user);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendUserJoinedChannel(byte[] chanID, byte[] user) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_USER_JOINED_CHAN);
            hdr.put((byte)chanID.length);
            hdr.put(chanID);
            hdr.put( (byte) user.length);
            hdr.put(user);
            fireSendBuffers(hdr);
        }
    }
    
    public void sendUserLeftChannel(byte[] chanID, byte[] user) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_USER_LEFT_CHAN);
            hdr.put((byte)chanID.length);
            hdr.put(chanID);
            hdr.put( (byte) user.length);
            hdr.put(user);
            fireSendBuffers(hdr);
        }
    }
    
    
    public void sendReconnectKey(byte[] id, byte[] key) throws IOException {
        synchronized (hdr) {
            hdr.clear();
            hdr.put(OP_RECONNECT_KEY);
            hdr.put((byte)id.length);
            hdr.put(id);
            hdr.put( (byte) key.length);
            hdr.put(key);
            fireSendBuffers(hdr);
        }
    }
    
    //NIOTCPConnectionListener
    
    private void fireSendBuffers(ByteBuffer buff){
    	fireSendBuffers(new ByteBuffer[] {buff});
    }
    
    private void fireSendBuffers(ByteBuffer[] buffs) {
    	ByteBuffer[] dup = new ByteBuffer[buffs.length];
		for(Iterator i=listeners.iterator();i.hasNext();){
			for(int i2=0;i2<buffs.length;i2++){
				dup[i2] = buffs[i2].duplicate();
				dup[i2].order(buffs[i2].order());
			}
			((TransportListener)(i.next())).sendBuffers(dup);
		}
		
	}

	/**
     * disconnected
     *
     * @param nIOTCPConnection NIOTCPConnection
     */
    public void disconnected(NIOTCPConnection nIOTCPConnection) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).disconnected();
        }
    }
    
    /**
     * packetReceived
     *
     * @param conn NIOTCPConnection
     * @param inputBuffer ByteBuffer
     */
    public void packetReceived(NIOTCPConnection conn, ByteBuffer buff) {
        byte op = buff.get();
        if (TRACEON){
            System.out.println("Recieved op: " + op);
            System.out.println("DataSize: " + buff.remaining());
        }
        switch (op) {
            case OP_UNICAST_MSG:
                boolean reliable = (buff.get() == 1);
                byte chanIDlen = buff.get();
                byte[] chanID = new byte[chanIDlen];
                buff.get(chanID);
                byte fromlen = buff.get();
                byte[] from = new byte[fromlen];
                buff.get(from);
                byte tolen = buff.get();
                byte[] to = new byte[tolen];
                buff.get(to);
                ByteBuffer databuff = buff.slice();
                fireUnicastMsg(reliable, chanID, from, to, databuff);
                break;
            case OP_MULTICAST_MSG:
                reliable = (buff.get() == 1);
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                fromlen = buff.get();
                from = new byte[fromlen];
                buff.get(from);
                byte tocount = buff.get();
                byte[][] tolist = new byte[tocount][];
                for (int i = 0; i < tocount; i++) {
                    tolen = buff.get();
                    tolist[i] = new byte[tolen];
                    buff.get(tolist[i]);
                }
                databuff = buff.slice();
                fireMultiicastMsg(reliable, chanID, from, tolist, databuff);
                break;
            case OP_BROADCAST_MSG:
                reliable = (buff.get() == 1);
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                fromlen = buff.get();
                from = new byte[fromlen];
                buff.get(from);
                databuff = buff.slice();
                fireBroadcastMsg(reliable, chanID, from, databuff);
                break;
            case OP_RECONNECT_REQ:
                int usrlen = buff.get();
                byte[] user = new byte[usrlen];
                buff.get(user);
                int keylen = buff.get();
                byte[] key = new byte[keylen];
                buff.get(key);
                fireReconnectReq(user, key);
                break;
            case OP_CONNECT_REQ:
                fireConnectReq();
                break;
            case OP_VALIDATION_REQ:
                Callback[] cbs = ValidationDataProtocol.unpackRequestData(buff);
                fireValidationReq(cbs);
                break;
            case OP_VALIDATION_RESP:
                cbs = ValidationDataProtocol.unpackRequestData(buff);
                fireValidationResp(cbs);
                break;
            case OP_USER_ACCEPTED:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                fireUserAccepted(user);
                break;
            case OP_USER_REJECTED:
                int bytelen = buff.getInt();
                byte[] msgbytes = new byte[bytelen];
                buff.get(msgbytes);
                fireUserRejected(new String(msgbytes));
                break;
            case OP_USER_JOINED:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                fireUserJoined(user);
                break;
            case OP_USER_LEFT:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                fireUserLeft(user);
                break;
            case OP_USER_JOINED_CHAN:
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                fireUserJoinedChan(chanID, user);
                break;
            case OP_USER_LEFT_CHAN:
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                fireUserLeftChan(chanID, user);
                break;
            case OP_RECONNECT_KEY:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                keylen = buff.get();
                key = new byte[keylen];
                buff.get(key);
                fireReconnectKeyRecieved(user,key);
                break;
            case OP_REQ_JOIN_CHAN:
                byte namelength = buff.get();
                byte[] namebytes = new byte[namelength];
                buff.get(namebytes);
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                fireReqJoinChan(new String(namebytes),user);
                break;
            case OP_JOINED_CHAN:
                namelength = buff.get();
                namebytes = new byte[namelength];
                buff.get(namebytes);
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                fireJoinedChan(new String(namebytes),chanID);
                break;                
            default:
                System.out.println("WARNING:Invalid op recieved from client: " + op +
                        " ignored.");
                break;
        }
    }
    
    /**
     * fireReconnectKeyRecieved
     *
     * @param key byte[]
     */
    private void fireReconnectKeyRecieved(byte[] user, byte[] key) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).reconnectKeyReceived(user, key);
        }
        
    }
    
    /**
     * fireUserLeft
     *
     * @param user byte[]
     */
    private void fireUserLeft(byte[] user) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).userLeft(user);
        }
    }
    
    /**
     * fireUserJoinedChan
     *
     * @param user byte[]
     */
    private void fireUserJoinedChan(byte[] chanID, byte[] user) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).userJoinedChannel(chanID, user);
        }
    }
    
    /**
     * fireUserLeftChan
     *
     * @param user byte[]
     */
    private void fireUserLeftChan(byte[] chanID, byte[] user) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).userLeftChannel(chanID, user);
        }
    }
    
    /**
     * fireUserJoined
     *
     * @param user byte[]
     */
    private void fireUserJoined(byte[] user) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).userJoined(user);
        }
    }
    
    /**
     * fireUserRejected
     */
    private void fireUserRejected(String message) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).userRejected(message);
        }
    }
    
    /**
     * fireUserAccepted
     *
     * @param user byte[]
     */
    private void fireUserAccepted(byte[] user) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).userAccepted(user);
        }
    }
    
    /**
     * fireValidationResp
     *
     * @param cbs Callback[]
     */
    private void fireValidationResp(Callback[] cbs) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).validationResponse(cbs);
        }
    }
    
    /**
     * fireValidationReq
     *
     * @param cbs Callback[]
     */
    private void fireValidationReq(Callback[] cbs) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).validationRequest(cbs);
        }
    }
    
    /**
     * fireReconnectReq
     *
     * @param user byte[]
     * @param key byte[]
     */
    private void fireReconnectReq(byte[] user, byte[] key) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).reconnectRequest(user, key);
        }
    }
    
    /**
     * fireConnectReq
     */
    private void fireConnectReq() {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).connectRequest();
        }
    }
    
    /**
     * fireBroadcastMsg
     *
     * @param reliable boolean
     * @param from byte[]
     * @param databuff ByteBuffer
     */
    private void fireBroadcastMsg(boolean reliable, byte[] chanID, byte[] from,
            ByteBuffer databuff) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).broadcastMsgReceived(chanID,reliable, from,
                    databuff.duplicate());
        }
        
    }
    
    /**
     * fireMultiicastMsg
     *
     * @param reliable boolean
     * @param from byte[]
     * @param tolist byte[][]
     * @param databuff ByteBuffer
     */
    private void fireMultiicastMsg(boolean reliable, byte[] chanID, byte[] from, byte[][] tolist,
            ByteBuffer databuff) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).multicastMsgReceived(chanID,reliable,
                    from,tolist,databuff.duplicate());
        }
    }
    
    /**
     * fireUnicastMsg
     *
     * @param reliable boolean
     * @param from byte[]
     * @param to byte[]
     * @param databuff ByteBuffer
     */
    private void fireUnicastMsg(boolean reliable, byte[] chanID, byte[] from, byte[] to,
            ByteBuffer databuff) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).unicastMsgReceived(chanID,reliable,
                    from,to,databuff.duplicate());
        }
    }
    
    
    private void fireReqJoinChan(String name, byte[] user) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).channelJoinReq(name,user);
        }
    }
    
    private void fireJoinedChan(String name, byte[] chanID) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            ( (TransportListener) i.next()).channelJoined(name,chanID);
        }
    }
    
    /**
     * disconnect
     */
    public void disconnect() throws IOException {
        conn.disconnect();
    }
    
    
    
}
