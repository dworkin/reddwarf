package com.sun.gi.comm.users.client;

import com.sun.gi.comm.discovery.Discoverer;
import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.discovery.DiscoveredUserManager;
import java.util.Set;
import java.util.HashSet;
import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;
import com.sun.gi.comm.validation.ValidationDataProtocol;

public class ClientConnectionManager
    implements UserManagerClientListener {
  Discoverer discoverer;
  UserManagerPolicy policy;
  private UserManagerClient umanager;
  private Class umanagerClass;
  private boolean done = false;
  private ByteBuffer validationData = null;
  private int gameID;
  private byte[] reconnectionKey = null;
  private ClientConnectionManagerListener listener;
  private byte[] myID;
  private boolean reconnecting = false;
  private boolean connected = false;

  public ClientConnectionManager(int gid, Discoverer disco) {
    this(gid, disco, new DefaultUserManagerPolicy());
  }

  public ClientConnectionManager(int gid, Discoverer disco,
                                 UserManagerPolicy policy) {
    discoverer = disco;
    this.policy = policy;
    gameID = gid;
  }

  public void setListener(ClientConnectionManagerListener l) {
    listener = l;
  }

  public String getGameName() {
    DiscoveredGame[] games = discoverer.games();
    for (int i = 0; i < games.length; i++) {
      if (games[i].getId() == gameID) {
        return games[i].getName();
      }
    }
    return null;
  }

  public String[] getUserManagerClassNames() {
    DiscoveredGame game = discoverGame(gameID);
    if (game == null) {
      return null;
    }
    DiscoveredUserManager[] umgrs = game.getUserManagers();
    if (umgrs == null) {
      return null;
    }
    Set names = new HashSet();
    for (int j = 0; j < umgrs.length; j++) {
      names.add(umgrs[j].getClientClass());
    }
    String[] outnames = new String[names.size()];
    return (String[]) names.toArray(outnames);

  }

  /**
   * discoverGame
   *
   * @param gameID int
   * @return DiscoveredGame
   */
  private DiscoveredGame discoverGame(int gameID) {
    DiscoveredGame[] games = discoverer.games();
    for (int i = 0; i < games.length; i++) {
      if (games[i].getId() == gameID) {
        return games[i];
      }
    }
    System.out.println("Discovery Error: No games discovered!");
    return null;
  }

  public boolean connect(String userManagerClassName) throws
      ClientAlreadyConnectedException {
    if (connected){
      throw new ClientAlreadyConnectedException("bad attempt to connect "+
                                                "when already connected.");
    }
    try {
      umanagerClass = getClass().forName(userManagerClassName);
      umanager = (UserManagerClient) umanagerClass.newInstance();
    }
    catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
    umanager.addListener(this);
    return connect(umanager);
  }

  private boolean connect(UserManagerClient umanager) {
    DiscoveredGame game = discoverGame(gameID);
    DiscoveredUserManager choice = policy.choose(game,
                                                 umanager.getClass().getName());
    umanager.connect(choice);
    return true;
  }

  /**
   * reconnect
   */
  private boolean reconnect() {
    return connect(umanager);
  }

  public void disconnect() {
    done = true;
    reconnecting = false;
    umanager.logout();
  }

  public void sendValidationResponse(Callback[] cbs) {
    umanager.validationDataResponse(cbs);
  }

  public void sendMulticastData(byte[] fromID, byte[][] toIDS, ByteBuffer buff,
                       boolean reliable) {
    umanager.sendMulticastData(fromID, toIDS, buff, reliable);
  }

  public void sendUnicastData(byte[] fromID, byte[] toID, ByteBuffer buff,
                       boolean reliable) {
    umanager.sendUnicastData(fromID, toID, buff, reliable);
  }


  public void broadcastData(byte[] fromID, ByteBuffer buff,
                       boolean reliable) {
    umanager.broadcastData(fromID, buff, reliable);
  }


// callbacks from UserClientManagerListener
  /**
   * dataReceived
   *
   * @param from ByteBuffer
   * @param data ByteBuffer
   */
  public void dataReceived(byte[] from, ByteBuffer data) {
    listener.dataRecieved(from, data);
  }

  /**
   * disconnected
   */
  public void disconnected() {
    connected = false;
    if (!done) {
      listener.failOverInProgress(myID);
      reconnect();
    }
    else {
      listener.disconnected();
    }
  }

  /**
   * connected
   *
   *
   */
  public void connected() {
    connected = true;
    if (!reconnecting) {
      umanager.login();
    }
    else {
      umanager.reconnectLogin(myID, reconnectionKey);
    }
    done = false;
    reconnecting = true;
  }

  /**
   * validationDataRequest
   *
   * @param dataRequest ByteBuffer
   */
  public void validationDataRequest(Callback[] cbs) {
    listener.validationRequest(cbs);

  }

  /**
   * loginAccepted
   *
   * @param userID ByteBuffer
   */
  public void loginAccepted(byte[] userID) {
    myID = new byte[userID.length];
    System.arraycopy(userID,0,myID,0,myID.length);
    listener.connected(myID);
  }

  /**
   * loginRejected
   *
   * @param userID ByteBuffer
   */
  public void loginRejected(String message) {
    listener.connectionRefused(message);
  }

  /**
   * userAdded
   *
   * @param userID ByteBuffer
   */
  public void userAdded(byte[] userID) {
    listener.userJoined(userID);
  }

  /**
   * userDropped
   *
   * @param userID ByteBuffer
   */
  public void userDropped(byte[] userID) {
    listener.userLeft(userID);
  }

  /**
   * newConnectionKeyIssued
   *
   * @param key long
   */
  public void newConnectionKeyIssued(byte[] key) {
    reconnectionKey = new byte[key.length];
    System.arraycopy(key,0,reconnectionKey,0,key.length);
  }

}
