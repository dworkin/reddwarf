package com.sun.gi.comm.users;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.utils.nio.*;
import com.sun.gi.comm.validation.UserValidator;
import com.sun.gi.comm.validation.UserValidatorFactory;
import com.sun.gi.utils.ReversableMap;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.transport.TransportListener;
import com.sun.gi.comm.transport.impl.TCPTransport;
import javax.security.auth.callback.Callback;
import com.sun.gi.comm.transport.Transport;
import sun.security.validator.Validator;
import javax.security.auth.callback.*;
import java.util.Map.Entry;

public class TCPIPUserManager
    implements UserManager, RouterListener, NIOSocketManagerListener,
  TransportListener {
  Router router;
  long gameID;
  ReversableMap idToConnectionMap = new ReversableMap();

  UserValidatorFactory validatorFactory;
  private ReversableMap idToTransport = new ReversableMap();
  private Map validatorMap = new HashMap();
  private String host="localhost";
  private int port=1139;
  private NIOSocketManager socketMgr;


  public TCPIPUserManager(Router router,Map params) throws
      InstantiationException {
    this.router = router;
    String p = (String)params.get("host");
    if (p!=null) {
      host =p;
    }
    p = (String)params.get("port");
    if (p!=null){
      port = Integer.parseInt(p);
    }
    init();
  }

  private void init () throws
      InstantiationException {
    router.addRouterListener(this);
    System.out.println("Starting TCPIP User Manager on host " + host + " port " + port );
    try {
      socketMgr = new NIOSocketManager();
      socketMgr.addListener(this);
      socketMgr.acceptTCPConnectionsOn(host,port);
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new InstantiationException("TCPIPUserManager failed to initialize");
    }

  }


  /**
   * getClientClassname
   *
   * @return String
   */
  public String getClientClassname() {
    return "com.sun.gi.comm.users.client.TCPIPUserManagerClient";
  }

  /**
   * getClientParams
   *
   * @return Map
   */
  public Map getClientParams() {
    Map params = new HashMap();
    params.put("host",host);
    params.put("port",Integer.toString(port));
    return params;
  }


  /**
   * setUserValidatorFactory
   *
   * @param validatorFactory UserValidatorFactory
   */
  public void setUserValidatorFactory(UserValidatorFactory validatorFactory) {
    this.validatorFactory = validatorFactory;
  }

  /**
   * disconnected
   *
   * @param tCPTransport TCPTransport
   */
  public void disconnected(TCPTransport tCPTransport) {
    UserID id = (UserID) idToTransport.reverseGet(tCPTransport);
    idToTransport.remove(id);
    router.disposeUser(id);
  }

  /**
   * reconnectKeyReceived
   *
   * @param tCPTransport TCPTransport
   * @param key byte[]
   */
  public void reconnectKeyReceived(TCPTransport transport, byte[] from,
                                   byte[] key) {

  }

  /**
   * userLeft
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userLeft(TCPTransport transport, byte[] user) {
    throw new UnsupportedOperationException();
  }

  /**
   * userJoined
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userJoined(TCPTransport tCPTransport, byte[] user) {
    throw new UnsupportedOperationException();
  }

  /**
   * userRejected
   *
   * @param tCPTransport TCPTransport
   */
  public void userRejected(TCPTransport tCPTransport, String message) {
    throw new UnsupportedOperationException();
  }

  /**
   * userAccepted
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userAccepted(TCPTransport tCPTransport, byte[] user) {
    throw new UnsupportedOperationException();
  }

  /**
   * validationResponse
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationResponse(TCPTransport transport, Callback[] cbs) {
    UserValidator v = (UserValidator) validatorMap.get(transport);
    v.dataResponse(cbs);
    cbs = v.nextDataRequest();
    try {
      if (cbs != null) {
        transport.sendValidationRequest(cbs);
      }
      else {
        if (v.authenticated()) {
          UserID newID = router.createUser();
          idToTransport.put(newID,transport);
          transport.sendUserAccepted(newID.toByteArray());
          byte[] key = router.initializeIDKey(newID);
          transport.sendReconnectKey(newID.toByteArray(),key);
        } else {
          transport.sendUserRejected("User authentication failed.");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * validationRequest
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationRequest(TCPTransport transport, Callback[] cbs) {
    throw new UnsupportedOperationException();
  }

  /**
   * reconnectRequest
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   * @param key byte[]
   */
  public void reconnectRequest(TCPTransport transport, byte[] user,
                               byte[] key) {
    try {
      UserID id = router.createUser(user);
      if (router.reregisterUser(id, key)) {
        System.out.println("Reregsitration succeeded");
        transport.sendUserAccepted(id.toByteArray());
        idToTransport.put(id,transport);
      } else {
        System.out.println("Reregistration failed.");
        transport.sendUserRejected("Session has expired, you must log back in.");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  /**
   * connectRequest
   *
   * @param tCPTransport TCPTransport
   */
  public void connectRequest(TCPTransport transport) {
    if (validatorFactory == null) { // no validators
      UserID id = router.createUser();
      try {
        idToTransport.put(id,transport);
        transport.sendUserAccepted(id.toByteArray());
        byte[] key = router.initializeIDKey(id);
        transport.sendReconnectKey(id.toByteArray(),key);
      }
      catch (IOException ex1) {
        ex1.printStackTrace();
      }
    } else {
      UserValidator v = validatorFactory.newValidator();
      validatorMap.put(transport, v);
      try {
        transport.sendValidationRequest(v.nextDataRequest());
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }
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
    UserID id = null;
    try {
      id = router.createUser(from);
      databuff.position(databuff.limit()); // make like we just wrote the packet
      router.broadcastData(id,databuff,reliable);
    }
    catch (InstantiationException ex) {
      ex.printStackTrace();
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
    try {
      UserID id = router.createUser(from);
      UserID[] to = new UserID[tolist.length];
      for (int i = 0; i < tolist.length; i++) {
        to[i] = router.createUser(tolist[i]);
      }
      databuff.position(databuff.limit()); // make like we just wrote the packet
      router.multicastData(to, id, databuff, reliable);
    } catch (Exception e){
      e.printStackTrace();
    }
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
    try {
      UserID fromID = router.createUser(from);
      UserID toID = router.createUser(to);
      databuff.position(databuff.limit()); // make like we just wrote the packet
      router.unicastData(toID, fromID, databuff, reliable);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // These are RouterListener methods

  /**
   * dataArrived
   *
   * @param to UserID
   * @param from UserID
   * @param data ByteBuffer
   * @param realiable boolean
   */
  public void dataArrived(ChannelID chan, UserID to, UserID from, ByteBuffer data,
                          boolean reliable) {
    data.position(data.limit());
    Transport transport = (Transport) idToTransport.get(to);
    if (transport != null) { //one of ours
      try {
        transport.sendUnicastMsg(chan.toByteArray, from.toByteArray(), to.toByteArray(), reliable,
                                 data.duplicate());
      }
      catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * userAdded
   *
   * @param userID UserID
   * @param local boolean
   */
  public void userAdded(UserID userID, boolean local) {
    for(Iterator i = idToTransport.entrySet().iterator();i.hasNext();){
      try {
        ( (Transport) i.next()).sendUserJoined(userID.toByteArray());
      }
      catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * userDropped
   *
   * @param userID UserID
   */
  public void userDropped(UserID userID) {
    for(Iterator i = idToTransport.entrySet().iterator();i.hasNext();){
      try {
        Entry entry = (Entry)i.next();
        ( (Transport) (entry.getValue())).sendUserLeft(userID.toByteArray());
      }
      catch (IOException ex) {
        ex.printStackTrace();
      }
    }

  }

  // NIOSocketManagerListener

  /**
   * connected
   *
   * @param connection NIOTCPConnection
   */
  public void connected(NIOTCPConnection connection) {
    throw new UnsupportedOperationException();
  }

  /**
   * newTCPConnection
   *
   * @param connection NIOTCPConnection
   */
  public void newTCPConnection(NIOTCPConnection connection) {
    Transport transport = new TCPTransport(connection);
    transport.addListener(this);
  }



  /**
   * connectionFailed
   *
   * @param connection NIOTCPConnection
   */
  public void connectionFailed(NIOTCPConnection connection) {
     throw new UnsupportedOperationException();
  }

  /**
   * newUserKey
   *
   * @param userID UserID
   * @param key byte[]
   */
  public void newUserKey(UserID userID, byte[] key) {
    Transport transport = (Transport) idToTransport.get(userID);
    if (transport == null) { // conenctiondied and router doesnt know yet
      router.disposeUser(userID);
    } else {
      try {
        transport.sendReconnectKey(userID.toByteArray(), key);
      }
      catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * userAdded
   *
   * @param userID UserID
   */
  public void userAdded(UserID userID) {
  }

  

}
