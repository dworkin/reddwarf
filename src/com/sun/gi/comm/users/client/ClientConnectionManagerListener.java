package com.sun.gi.comm.users.client;

import java.nio.*;
import javax.security.auth.callback.*;

public interface ClientConnectionManagerListener {
  public void validationRequest(Callback[] callbacks);
  public void connected(byte[] myID);
  public void connectionRefused(String message);
  public void disconnected();
  public void dataRecieved(byte[] from, ByteBuffer data);

  /**
   * userJoined
   *
   * @param userID byte[]
   */
  public void userJoined(byte[] userID);

  /**
   * userLeft
   *
   * @param userID byte[]
   */
  public void userLeft(byte[] userID);

  

}
