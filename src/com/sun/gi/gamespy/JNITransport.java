package com.sun.gi.gamespy;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class JNITransport {
  private static List listeners = new ArrayList();

  public static void initialize()
  {
    System.err.println("init GameSpy");
    System.loadLibrary("GameSpyJNI");
    init();
  }

  public JNITransport() throws InstantiationException {
    throw new InstantiationException("Transport is a static interface to "+
                                     "native code and cannot be instantiated.");
  }

  public static void addListener(TransportListener l){
    listeners.add(l);
  }

  // JNI stubs
  private static native void init();

  public static native void gt2Accept(long connHandle);

  public static native void gt2CloseAllConnections(long socketHandle);

  public static native void gt2CloseAllConnectionsHard(long socketHandle);

  public static native void gt2CloseConnection(long connHandle);

  public static native void gt2CloseConnectionHard(long connHandle);

  public static native void gt2CloseSocket(long socketHandle);

  public static native long gt2Connect(long socketHandle, String remoteAddr,
                                byte[] message, int msgLength, int timeout);

  public static native long gt2CreateSocket(String localAddr, int outBuffSz,
                                     int inBuffSz);

  public static native void gt2Listen(long socketHandle);

  public static native void gt2Reject(long connectionHandle, byte[] message,
                               int mesgLength);

  public static native void gt2Send(long connectionHandle, byte[] message,
                             int msgLength, boolean reliable);

  public static native void gt2Think(long socketHandle);

  public static native long lastResult();

  // callabcks from JNI code

  public static void gt2SocketErrorCallback(long socketHandle) {
    fireSocketError(socketHandle);
  }

  public static void gt2ConnectedCallback(long connectionHandle, int result,
                                   byte[] message, int msgLength) {
    fireConnected(connectionHandle, result, message, msgLength);
  }

  public static void gt2ClosedCallback(long connectionHandle, int reason) {
    fireClosed(connectionHandle, reason);
  }

  public static void gt2PingCallback(long connectionHandle, int latency) {
    firePing(connectionHandle, latency);
  }

  public static void gt2ConnectAttemptCallback(long socketHandle,
                                        long connectionHandle,
                                        long ip, short port, int latency,
                                        byte[] message, int msgLength) {
    fireConnectAttempt(socketHandle, connectionHandle, ip, port, latency,
                       message, msgLength);

  }

  //  callback logic


  /**
   * fireSocketError
   *
   * @param socketHandle long
   */
  private static void fireSocketError(long socketHandle) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).socketError(socketHandle);
    }
  }

  /**
   * fireConnected
   *
   * @param connectionHandle long
   * @param result long
   * @param message byte[]
   * @param msgLength int
   */
  private static void fireConnected(long connectionHandle, long result, byte[] message,
                             int msgLength) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).connected(connectionHandle, result,
                                                message,
                                                msgLength);
    }

  }

  /**
   * fireClosed
   *
   * @param connectionHandle long
   * @param reason long
   */
  private static void fireClosed(long connectionHandle, long reason) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).closed(connectionHandle, reason);
    }

  }

  /**
   * firePing
   *
   * @param connectionHandle long
   * @param latency int
   */
  private static void firePing(long connectionHandle, int latency) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).ping(connectionHandle, latency);
    }

  }

  /**
   * fireConnectAttempt
   *
   * @param socketHandle long
   * @param connectionHandle long
   * @param ip long
   * @param port short
   * @param latency int
   * @param message byte[]
   * @param msgLength int
   */
  private static void fireConnectAttempt(long socketHandle, long connectionHandle,
                                  long ip, short port, int latency,
                                  byte[] message, int msgLength) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (TransportListener) i.next()).connectAttempt(socketHandle,
          connectionHandle,
          ip, port, latency, message, msgLength);
    }

  }

}
