package com.sun.gi.comm.users.client.umanager;

import java.io.*;
import java.nio.*;
import java.util.*;
import javax.security.auth.callback.*;

import com.sun.gi.comm.discovery.*;
import com.sun.gi.comm.transport.*;
import com.sun.gi.comm.transport.impl.*;
import com.sun.gi.utils.nio.*;
import com.sun.gi.comm.users.client.*;

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

    public void userRejected(String reason) {
    }

    public void joinChannel(String channelName) {
    }

    public void channelJoinReq(String chanName, byte[] user) {
    }

    public void channelJoined(String chanName, byte[] chanID) {
    }

    public void broadcastMsgReceived(byte[] chanID, boolean reliable, byte[] from, ByteBuffer databuff) {
    }

    public void multicastMsgReceived(byte[] chanID, boolean reliable, byte[] from, byte[][] tolist, ByteBuffer databuff) {
    }

    public void unicastMsgReceived(byte[] chanID, boolean reliable, byte[] from, byte[] to, ByteBuffer databuff) {
    }

    public void userLeft(byte[] user) {
    }

    public void userJoined(byte[] user) {
    }

    public void userAccepted(byte[] user) {
    }

    public void validationResponse(Callback[] cbs) {
    }

    public void validationRequest(Callback[] cbs) {
    }

    public void connect(DiscoveredUserManager choice, UserManagerClientListener listener) {
    }

    public void sendToServer(byte[] from, ByteBuffer buff, boolean reliable) {
    }

    public void userLeftChannel(byte[] chanID, byte[] user) {
    }

    public void userJoinedChannel(byte[] chanID, byte[] user) {
    }

    public void connectRequest() {
    }

    public void disconnected() {
    }

    public void reconnectKeyReceived(byte[] user, byte[] key) {
    }

    public void reconnectRequest(byte[] user, byte[] key) {
    }

    public void newTCPConnection(NIOTCPConnection connection) {
        throw new UnsupportedOperationException("This is not a server socket.");
    }

    
  
}
