package com.sun.gi.comm.transport.impl;

import java.io.*;
import java.nio.*;
import java.util.*;
import javax.security.auth.callback.*;

import com.sun.gi.comm.transport.*;
import com.sun.gi.comm.validation.*;
import com.sun.gi.utils.nio.*;

public class TCPTransport
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

  private NIOTCPConnection conn;
  private ByteBuffer hdr;
  private ByteBuffer[] sendArray = new ByteBuffer[2];
  private List listeners = new ArrayList();
  private boolean ready = false;
  private static final boolean TRACEON = false;

  public TCPTransport(NIOTCPConnection conn){
    hdr = ByteBuffer.allocate(2048);
    sendArray[0] = hdr;
    this.conn = conn;
    conn.addListener(this);
  }

  public void addListener(TransportListener l) {
    listeners.add(l);
  }

  public synchronized void sendUnicastMsg(byte[] from,
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
      hdr.put( (byte) from.length);
      hdr.put(from);
      hdr.put( (byte) to.length);
      hdr.put(to);
      sendArray[1] = data;
      conn.send(sendArray);
    }
  }

  public synchronized void sendMulticastMsg(byte[] from,
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
      hdr.put( (byte) from.length);
      hdr.put(from);
      hdr.put( (byte) to.length);
      for (int i = 0; i < to.length; i++) {
        hdr.put( (byte) (to[i].length));
        hdr.put(to[i]);
      }
      sendArray[1] = data;
      conn.send(sendArray);
    }
  }

  public synchronized void sendBroadcastMsg(byte[] from,
                               boolean reliable,
                               ByteBuffer data) throws IOException {
    // buffers coming into here should be in "written" state and full
    // position == limit
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_BROADCAST_MSG);
      hdr.put( (byte) (reliable ? 1 : 0));
      hdr.put( (byte) from.length);
      hdr.put(from);
      sendArray[1] = data;
      conn.send(sendArray);
    }
  }

  public void sendConnectionRequest() throws IOException {
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_CONNECT_REQ);
      conn.send(hdr);
    }
  }

  public void sendUserAccepted( byte[] newID) throws
      IOException {
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_USER_ACCEPTED);
      hdr.put( (byte) newID.length);
      hdr.put(newID);
      conn.send(hdr);
    }
  }

  public void sendUserRejected(String message) throws IOException {
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_USER_REJECTED);
      byte[] msgbytes = message.getBytes();
      hdr.putInt(msgbytes.length);
      hdr.put(msgbytes);
      conn.send(hdr);
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
      conn.send(hdr);
    }
  }

  public void sendValidationRequest(Callback[] cbs) throws
      UnsupportedCallbackException, IOException {
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_VALIDATION_REQ);
      ValidationDataProtocol.makeRequestData(hdr, cbs);
      conn.send(hdr);
    }
  }

  public void sendValidationResponse(Callback[] cbs) throws
      UnsupportedCallbackException, IOException {
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_VALIDATION_RESP);
      ValidationDataProtocol.makeRequestData(hdr, cbs);
      conn.send(hdr);
    }
  }

  public void sendUserJoined(byte[] user) throws IOException {
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_USER_JOINED);
      hdr.put( (byte) user.length);
      hdr.put(user);
      conn.send(hdr);
    }
  }

  public void sendUserLeft(byte[] user) throws IOException {
    synchronized (hdr) {
      hdr.clear();
      hdr.put(OP_USER_LEFT);
      hdr.put( (byte) user.length);
      hdr.put(user);
      conn.send(hdr);
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
      conn.send(hdr);
    }
  }

  //NIOTCPConnectionListener

  /**
   * disconnected
   *
   * @param nIOTCPConnection NIOTCPConnection
   */
  public void disconnected(NIOTCPConnection nIOTCPConnection) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).disconnected(this);
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
        byte fromlen = buff.get();
        byte[] from = new byte[fromlen];
        buff.get(from);
        byte tolen = buff.get();
        byte[] to = new byte[tolen];
        buff.get(to);
        ByteBuffer databuff = buff.slice();
        fireUnicastMsg(reliable, from, to, databuff);
        break;
      case OP_MULTICAST_MSG:
        reliable = (buff.get() == 1);
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
        fireMultiicastMsg(reliable, from, tolist, databuff);
        break;
      case OP_BROADCAST_MSG:
        reliable = (buff.get() == 1);
        fromlen = buff.get();
        from = new byte[fromlen];
        buff.get(from);
        databuff = buff.slice();
        fireBroadcastMsg(reliable, from, databuff);
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
      case OP_RECONNECT_KEY:
        usrlen = buff.get();
        user = new byte[usrlen];
        buff.get(user);
        keylen = buff.get();
        key = new byte[keylen];
        buff.get(key);
        fireReconnectKeyRecieved(user,key);
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
      ( (TransportListener) i.next()).reconnectKeyReceived(this,user, key);
    }

  }

  /**
   * fireUserLeft
   *
   * @param user byte[]
   */
  private void fireUserLeft(byte[] user) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).userLeft(this, user);
    }
  }

  /**
   * fireUserJoined
   *
   * @param user byte[]
   */
  private void fireUserJoined(byte[] user) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).userJoined(this, user);
    }
  }

  /**
   * fireUserRejected
   */
  private void fireUserRejected(String message) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).userRejected(this,message);
    }
  }

  /**
   * fireUserAccepted
   *
   * @param user byte[]
   */
  private void fireUserAccepted(byte[] user) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).userAccepted(this, user);
    }
  }

  /**
   * fireValidationResp
   *
   * @param cbs Callback[]
   */
  private void fireValidationResp(Callback[] cbs) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).validationResponse(this, cbs);
    }
  }

  /**
   * fireValidationReq
   *
   * @param cbs Callback[]
   */
  private void fireValidationReq(Callback[] cbs) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).validationRequest(this, cbs);
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
      ( (TransportListener) i.next()).reconnectRequest(this, user, key);
    }
  }

  /**
   * fireConnectReq
   */
  private void fireConnectReq() {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).connectRequest(this);
    }
  }

  /**
   * fireBroadcastMsg
   *
   * @param reliable boolean
   * @param from byte[]
   * @param databuff ByteBuffer
   */
  private void fireBroadcastMsg(boolean reliable, byte[] from,
                                ByteBuffer databuff) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).broadcastMsgReceived(reliable,
          from,databuff.duplicate());
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
  private void fireMultiicastMsg(boolean reliable, byte[] from, byte[][] tolist,
                                 ByteBuffer databuff) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).multicastMsgReceived(reliable,
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
  private void fireUnicastMsg(boolean reliable, byte[] from, byte[] to,
                              ByteBuffer databuff) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).unicastMsgReceived(reliable,
          from,to,databuff.duplicate());
    }
  }

  /**
   * disconnect
   */
  public void disconnect() throws IOException {
    conn.disconnect();
  }

}
