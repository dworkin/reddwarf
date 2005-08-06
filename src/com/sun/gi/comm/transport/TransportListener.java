package com.sun.gi.comm.transport;

import java.nio.*;
import javax.security.auth.callback.*;

import com.sun.gi.comm.transport.impl.*;

public interface TransportListener {

  /**
   * disconnected
   *
   * @param tCPTransport TCPTransport
   */
  public void disconnected();

  /**
   * reconnectKeyReceived
   *
   * @param tCPTransport TCPTransport
   * @param key byte[]
   */
  public void reconnectKeyReceived(byte[] user, byte[] key);

  /**
   * userLeft
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userLeft(byte[] user);

  /**
   * userJoined
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userJoined(byte[] user);
  
  
  /**
   * userLeftChannel
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userLeftChannel(byte[] chanID, byte[] user);

  /**
   * userJoined
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userJoinedChannel(byte[] chanID, byte[] user);

  /**
   * userRejected
   *
   * @param tCPTransport TCPTransport
   */
  public void userRejected(String reason);

  /**
   * userAccepted
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userAccepted(byte[] user);

  /**
   * validationResponse
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationResponse(Callback[] cbs);

  /**
   * validationRequest
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationRequest(Callback[] cbs);

  /**
   * reconnectRequest
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   * @param key byte[]
   */
  public void reconnectRequest(byte[] user,
                               byte[] key);

  /**
   * connectRequest
   *
   * @param tCPTransport TCPTransport
   */
  public void connectRequest();
  
  /**
   * channelJoinRequest
   *
   * 
   */
  
  public void channelJoinReq(String chanName, byte[] user);
  
  public void channelJoined(String chanName, byte[] chanID);
  
  

  /**
   * broadcastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param databuff ByteBuffer
   */
  public void broadcastMsgReceived(byte[] chanID, boolean reliable, byte[] from,
                                   ByteBuffer databuff);

  /**
   * multicastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param tolist byte[][]
   * @param databuff ByteBuffer
   */
  public void multicastMsgReceived(byte[] chanID, boolean reliable, byte[] from,
                                   byte[][] tolist, ByteBuffer databuff);

  /**
   * unicastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param to byte[]
   * @param databuff ByteBuffer
   */
  public void unicastMsgReceived(byte[] chanID, boolean reliable, byte[] from, byte[] to,
                                 ByteBuffer databuff);

}
