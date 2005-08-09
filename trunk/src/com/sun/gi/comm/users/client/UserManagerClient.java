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
    
  public void connect(DiscoveredUserManager choice,UserManagerClientListener listener);  
  public void login();
  public void validationDataResponse(Callback[] cbs);
  public void logout();
  public void joinChannel(byte[] user, String channelName);
  public void sendToServer(byte[] from, ByteBuffer buff,
                       boolean reliable);

  /**
   * reconnectLogin
   *
   * @param reconnectionKey long
   */
  public void reconnectLogin(byte[] userID, byte[] reconnectionKey);

  
 

}
