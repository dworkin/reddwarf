package com.sun.gi.utils.test;

import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOTCPConnectionListener;
import com.sun.gi.utils.nio.NIOTCPConnection;
import java.nio.ByteBuffer;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.*;

public class NIOTCPTestClient implements NIOTCPConnectionListener {
  NIOSocketManager socketManager;
  NIOTCPConnection conn;
  ByteBuffer outputBuffer = ByteBuffer.allocate(2048);
  public NIOTCPTestClient() {
    try {
      socketManager = new NIOSocketManager();
    }
    catch (IOException ex1) {
      ex1.printStackTrace();
      System.exit(1);
    }
    conn = socketManager.makeTCPConnectionTo("localhost",1138);
    conn.addListener(this);
    BufferedReader rdr =
    new BufferedReader(new InputStreamReader(System.in));

    while(true){
      try {

        String line = rdr.readLine()+"\n";
        outputBuffer.clear();
        outputBuffer.put(line.getBytes());
        conn.send(outputBuffer);
      }
      catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  static public void main(String[] args){
    new NIOTCPTestClient();
  }

  /**
   * connected
   *
   * @param conn NIOTCPConnection
   */
  public void connected(NIOTCPConnection conn) {
    System.out.println("COnnected!");
  }

  /**
   * connectionFailed
   *
   * @param conn NIOTCPConnection
   */
  public void connectionFailed(NIOTCPConnection conn) {
      System.out.println("Failed to connect!");
  }

  /**
   * packetReceived
   *
   * @param conn NIOTCPConnection
   * @param inputBuffer ByteBuffer
   */
  public void packetReceived(NIOTCPConnection conn, ByteBuffer inputBuffer) {
    byte[] inbytes=new byte[inputBuffer.remaining()];
    inputBuffer.get(inbytes);
    System.out.println("Received: "+new String(inbytes));
  }

  /**
   * disconnected
   *
   * @param nIOTCPConnection NIOTCPConnection
   */
  public void disconnected(NIOTCPConnection nIOTCPConnection) {
    System.out.println("Disconnected!");
  }

}

