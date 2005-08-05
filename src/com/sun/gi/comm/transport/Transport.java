package com.sun.gi.comm.transport;

import java.nio.ByteBuffer;
import java.io.IOException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

public interface Transport {
  public void addListener(TransportListener l);

  public void sendUnicastMsg(byte[] chanID, byte[] from,
                             byte[] to,
                             boolean reliable, ByteBuffer data) throws
      IOException;

  public void sendMulticastMsg(byte[] chanID, byte[] from,
                               byte[][] to,
                               boolean reliable,
                               ByteBuffer data) throws IOException;

  public void sendBroadcastMsg(byte[] chanID, byte[] from,
                               boolean reliable,
                               ByteBuffer data) throws IOException;

  public void sendConnectionRequest() throws IOException;

  public void sendUserAccepted(byte[] newID) throws
      IOException;

  public void sendUserRejected(String message) throws IOException;

  public void sendReconnectRequest(byte[] from,
                                   byte[] reconnectionKey) throws
      IOException;

  public void sendValidationRequest(Callback[] cbs) throws
      UnsupportedCallbackException, IOException;

  public void sendValidationResponse(Callback[] cbs) throws
      UnsupportedCallbackException, IOException;

  public void sendUserJoined(byte[] user) throws IOException;

  public void sendUserLeft(byte[] user) throws IOException;
  
  public void sendUserJoinChan(String chanName, byte[] user) throws IOException;
  
  public void sendUserJoinedChannel(byte[] chanID, byte[] user) throws IOException;
  
  public void sendUserLeftChannel(byte[] chanID, byte[] user) throws IOException;

  public void sendReconnectKey(byte[] user, byte[] key) throws IOException;

}
