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
  public void disconnected(TCPTransport tCPTransport);

  /**
   * reconnectKeyReceived
   *
   * @param tCPTransport TCPTransport
   * @param key byte[]
   */
  public void reconnectKeyReceived(TCPTransport tCPTransport, byte[] user, byte[] key);

  /**
   * userLeft
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userLeft(TCPTransport tCPTransport, byte[] user);

  /**
   * userJoined
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userJoined(TCPTransport tCPTransport, byte[] user);

  /**
   * userRejected
   *
   * @param tCPTransport TCPTransport
   */
  public void userRejected(TCPTransport tCPTransport, String reason);

  /**
   * userAccepted
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   */
  public void userAccepted(TCPTransport tCPTransport, byte[] user);

  /**
   * validationResponse
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationResponse(TCPTransport tCPTransport, Callback[] cbs);

  /**
   * validationRequest
   *
   * @param tCPTransport TCPTransport
   * @param cbs Callback[]
   */
  public void validationRequest(TCPTransport tCPTransport, Callback[] cbs);

  /**
   * reconnectRequest
   *
   * @param tCPTransport TCPTransport
   * @param user byte[]
   * @param key byte[]
   */
  public void reconnectRequest(TCPTransport tCPTransport, byte[] user,
                               byte[] key);

  /**
   * connectRequest
   *
   * @param tCPTransport TCPTransport
   */
  public void connectRequest(TCPTransport tCPTransport);

  /**
   * broadcastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param databuff ByteBuffer
   */
  public void broadcastMsgReceived(boolean reliable, byte[] from,
                                   ByteBuffer databuff);

  /**
   * multicastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param tolist byte[][]
   * @param databuff ByteBuffer
   */
  public void multicastMsgReceived(boolean reliable, byte[] from,
                                   byte[][] tolist, ByteBuffer databuff);

  /**
   * unicastMsgReceived
   *
   * @param reliable boolean
   * @param from byte[]
   * @param to byte[]
   * @param databuff ByteBuffer
   */
  public void unicastMsgReceived(boolean reliable, byte[] from, byte[] to,
                                 ByteBuffer databuff);

}
