package com.sun.gi.comm.users.client;

import java.nio.*;
import javax.security.auth.callback.*;

import com.sun.gi.comm.discovery.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface UserManagerClient {
  /**
   * connect
   *
   * @param choice DiscoveredUserManager
   */
  public void connect(DiscoveredUserManager choice);

  /**
   * addListener
   *
   * @param listener UserManagerClientListener
   */
  public void addListener(UserManagerClientListener listener);

  public void sendMulticastData(byte[] from,byte[][] to, ByteBuffer buff,
                       boolean reliable);

  public void broadcastData(byte[] from, ByteBuffer buff,
                       boolean reliable);

  public void login();
  public void validationDataResponse(Callback[] cbs);
  public void logout();


  /**
   * reconnectLogin
   *
   * @param reconnectionKey long
   */
  public void reconnectLogin(byte[] userID, byte[] reconnectionKey);

  /**
   * sendData
   *
   * @param fromID byte[]
   * @param toID byte[]
   * @param buff ByteBuffer
   * @param reliable boolean
   */
  public void sendUnicastData(byte[] fromID, byte[] toID, ByteBuffer buff,
                       boolean reliable);

}
