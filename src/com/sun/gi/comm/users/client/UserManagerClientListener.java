package com.sun.gi.comm.users.client;


import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface UserManagerClientListener {
  public void connected();
  public void disconnected();
  public void newConnectionKeyIssued(byte[] key);
  public void validationDataRequest(Callback[] cbs);
  public void loginAccepted(byte[] userID);
  public void loginRejected(String message);
  public void userAdded(byte[] userID);
  public void userDropped(byte[] userID);
  public void dataReceived(byte[] from,ByteBuffer data);
}
