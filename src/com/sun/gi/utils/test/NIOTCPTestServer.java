package com.sun.gi.utils.test;

import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;
import com.sun.gi.utils.nio.NIOTCPConnection;
import java.io.*;
import com.sun.gi.utils.nio.NIOTCPConnectionListener;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class NIOTCPTestServer
    implements NIOSocketManagerListener,
    NIOTCPConnectionListener {
  NIOSocketManager socketMgr;
  private List connections = new ArrayList();
  public NIOTCPTestServer() {
    try {
      socketMgr = new NIOSocketManager();
      socketMgr.addListener(this);
      socketMgr.acceptTCPConnectionsOn("localhost", 1138);

    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  static public void main(String[] args) {
    new NIOTCPTestServer();
  }

  /**
   * newTCPConnection
   *
   * @param connection NIOTCPConnection
   */
  public void newTCPConnection(NIOTCPConnection connection) {
    System.out.println("Someone connected!");
    connection.addListener(this);
    connections.add(connection);
  }

  /**
   * connected
   *
   * @param conn NIOTCPConnection
   */
  public void connected(NIOTCPConnection conn) {
    System.out.println("Weird, got connected callback");
  }

  /**
   * connectionFailed
   *
   * @param conn NIOTCPConnection
   */
  public void connectionFailed(NIOTCPConnection conn) {
    System.out.println("Weird, got connection failed callback");
  }

  /**
   * packetReceived
   *
   * @param conn NIOTCPConnection
   * @param inputBuffer ByteBuffer
   */
  public void packetReceived(NIOTCPConnection conn, ByteBuffer inputBuffer) {
    byte[] inbytes = new byte[inputBuffer.remaining()];
    inputBuffer.get(inbytes);
    System.out.println("Received: " + new String(inbytes));
    for (Iterator i = connections.iterator(); i.hasNext(); ) {
      NIOTCPConnection tconn = (NIOTCPConnection) i.next();
      if (tconn != conn) { // dont send back to sender
        try {
          tconn.send(inputBuffer);
        }
        catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  /**
   * disconnected
   *
   * @param nIOTCPConnection NIOTCPConnection
   */
  public void disconnected(NIOTCPConnection conn) {
    System.out.println("Socket disconnected!");
    connections.remove(conn);
  }

}
