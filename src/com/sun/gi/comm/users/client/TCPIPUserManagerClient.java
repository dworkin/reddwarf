package com.sun.gi.comm.users.client;

import java.io.*;
import java.nio.*;
import java.util.*;
import javax.security.auth.callback.*;

import com.sun.gi.comm.discovery.*;
import com.sun.gi.comm.transport.*;
import com.sun.gi.comm.transport.impl.*;
import com.sun.gi.utils.nio.*;

public class TCPIPUserManagerClient
    implements UserManagerClient, TransportListener, NIOSocketManagerListener {
  NIOSocketManager mgr;
  TCPTransport transport;
  List listeners = new ArrayList();

  public TCPIPUserManagerClient() throws InstantiationException {
    try {
      mgr = new NIOSocketManager();
      mgr.addListener(this);
    }
    catch (IOException ex) {
      throw new InstantiationException(ex.getMessage());
    }
  }

  /**
   *
   * @param choice DiscoveredUserManager
   * @todo Implement this com.sun.gi.comm.users.client.UserManagerClient method
   */
  public void connect(DiscoveredUserManager choice) {
    String host = choice.getParameter("host");
    int port = Integer.parseInt(choice.getParameter("port"));
    System.out.println("Attempting to connect to a TCPIP User Manager on host " +
                       host + " port " + port);
    mgr.makeTCPConnectionTo(host, port);
  }

  /**
   *
   * @param listener UserManagerClientListener
   * @todo Implement this com.sun.gi.comm.users.client.UserManagerClient method
   */
  public void addListener(UserManagerClientListener listener) {
    listeners.add(listener);
  }

  /**
   * sendData
   *
   * @param from byte[]
   * @param to byte[]
   * @param buff ByteBuffer
   * @param reliable boolean
   * @todo Implement this com.sun.gi.comm.users.client.UserManagerClient method
   */
  public void sendMulticastData(byte[] from, byte[][] to, ByteBuffer buff,
                       boolean reliable) {
    try {
      transport.sendMulticastMsg(from, to, reliable, buff);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * login
   *
   * @todo Implement this com.sun.gi.comm.users.client.UserManagerClient method
   */
  public void login() {
    try {
      transport.sendConnectionRequest();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * validationDataResponse
   *
   * @param data ByteBuffer
   * @todo Implement this com.sun.gi.comm.users.client.UserManagerClient method
   */
  public void validationDataResponse(Callback[] cbs) {
    try {
      transport.sendValidationResponse(cbs);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
    catch (UnsupportedCallbackException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * logout
   *
   * @todo Implement this com.sun.gi.comm.users.client.UserManagerClient method
   */
  public void logout() {
    try {
      transport.disconnect();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   *
   * @param reconnectionKey long
   * @todo Implement this com.sun.gi.comm.users.client.UserManagerClient method
   */
  public void reconnectLogin(byte[] userID, byte[] reconnectionKey) {
    try {
      transport.sendReconnectRequest(userID, reconnectionKey);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  // TransportListener methods
  /**
   * reconnectLogin
   *
   * @param reconnectionKey long
   */
  public void reconnectLogin(long reconnectionKey) {
    throw new UnsupportedOperationException();
  }

  /**
   * broadcastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param databuff ByteBuffer
   */
  public void broadcastMsgReceived(boolean reliable, byte[] from,
                                   ByteBuffer databuff) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).dataReceived(from, databuff);
    }
  }

  /**
   * connectRequest
   *
   * @param tCPTransport TCPTransport
   */
  public void connectRequest(TCPTransport tCPTransport) {
    throw new UnsupportedOperationException();
  }

  /**
   * connected
   *
   * @param tCPTransport TCPTransport
   */
  public void connected(TCPTransport tCPTransport) {

  }

  /**
   * connectionFailed
   *
   * @param tCPTransport TCPTransport
   */
  public void connectionFailed(TCPTransport tCPTransport) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).disconnected();
    }
  }

  /**
   * disconnected
   *
   * @param tCPTransport TCPTransport
   */
  public void disconnected(TCPTransport tCPTransport) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).disconnected();
    }
  }

  /**
   * multicastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param tolist byte[][]
   * @param databuff ByteBuffer
   */
  public void multicastMsgReceived(boolean reliable, byte[] from,
                                   byte[][] tolist, ByteBuffer databuff) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).dataReceived(from, databuff);
    }
  }

  /**
   * reconnectKeyReceived
   *
   * @param tCPTransport TCPTransport
   * @param key byte[]
   */
  public void reconnectKeyReceived(TCPTransport tCPTransport, byte[] user,
                                   byte[] key) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).newConnectionKeyIssued(key);
    }
  }

  /**
   * reconnectRequest
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   * @param key byte[]
   */
  public void reconnectRequest(TCPTransport tCPTransport, byte[] user,
                               byte[] key) {
    throw new UnsupportedOperationException();
  }

  /**
   * unicastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param to byte[]
   * @param databuff ByteBuffer
   */
  public void unicastMsgReceived(boolean reliable, byte[] from, byte[] to,
                                 ByteBuffer databuff) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).dataReceived(from, databuff);
    }
  }

  /**
   * userAccepted
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userAccepted(TCPTransport tCPTransport, byte[] user) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).loginAccepted(user);
    }
  }

  /**
   * userJoined
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userJoined(TCPTransport tCPTransport, byte[] user) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).userAdded(user);
    }
  }

  /**
   * userLeft
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userLeft(TCPTransport tCPTransport, byte[] user) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).userDropped(user);
    }
  }

  /**
   * userRejected
   *
   * @param tCPTransport TCPTransport
   */
  public void userRejected(TCPTransport tCPTransport, String message) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).loginRejected(message);
    }

  }

  /**
   * validationRequest
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationRequest(TCPTransport tCPTransport, Callback[] cbs) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).validationDataRequest(cbs);
    }
  }

  /**
   * validationResponse
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationResponse(TCPTransport tCPTransport, Callback[] cbs) {
    throw new UnsupportedOperationException();
  }

  /**
   * broadcastData
   *
   * @param from byte[]
   * @param buff ByteBuffer
   * @param reliable boolean
   */
  public void broadcastData(byte[] from, ByteBuffer buff, boolean reliable) {
    try {
      transport.sendBroadcastMsg(from, reliable, buff);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }

  }

  /**
   * newTCPConnection
   *
   * @param connection NIOTCPConnection
   */
  public void newTCPConnection(NIOTCPConnection connection) {
    throw new UnsupportedOperationException();
  }

  /**
   * connected
   *
   * @param connection NIOTCPConnection
   */
  public void connected(NIOTCPConnection connection) {
    System.out.println("connected");
    transport = new TCPTransport(connection);
    transport.addListener(this);
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).connected();
    }
  }

  /**
   * connectionFailed
   *
   * @param connection NIOTCPConnection
   */
  public void connectionFailed(NIOTCPConnection connection) {
    System.out.println("Failed to connect!");
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (UserManagerClientListener) i.next()).disconnected();
    }
  }

  /**
   * sendData
   *
   * @param fromID byte[]
   * @param toID byte[]
   * @param buff ByteBuffer
   * @param reliable boolean
   */
  public void sendUnicastData(byte[] fromID, byte[] toID, ByteBuffer buff,
                       boolean reliable) {
    try {
      transport.sendUnicastMsg(fromID, toID, reliable, buff);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }

  }
}
