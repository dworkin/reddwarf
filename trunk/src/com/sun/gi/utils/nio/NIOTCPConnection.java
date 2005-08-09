package com.sun.gi.utils.nio;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class NIOTCPConnection {
  private SocketChannel chan;
  private ByteBuffer inputBuffer; // made available for filling by the manager
  private ByteBuffer sizeHeader ;
  private int currentPacketSize = -1;
  private List listeners = new ArrayList();
  private ByteBuffer outputHeader = ByteBuffer.allocate(4);
  private boolean connected = false;

  // package private factory

  static NIOTCPConnection newConnectionObject(SocketChannel socketChannel,
                                              int initialMaxPacketSz){
    return new NIOTCPConnection(socketChannel,initialMaxPacketSz);
  }

  private NIOTCPConnection(SocketChannel socketChannel, int initialMaxPacketSize) {
    inputBuffer = ByteBuffer.allocate(initialMaxPacketSize);
    sizeHeader = ByteBuffer.allocate(4);
    chan = socketChannel;
  }

  /**
   * NIOTCPConnection
   *
   * @param socketChannel SocketChannel
   */
  private NIOTCPConnection(SocketChannel socketChannel) {
    this(socketChannel,2048);
  }

  /**
   * dataArrived
   *
   * @param inputBuffer ByteBuffer
   */
  public void dataArrived() throws IOException {
    int bytesRead=-1;
    do {
      if (currentPacketSize == -1) {// getting size
        bytesRead = chan.read(sizeHeader);
        if (!sizeHeader.hasRemaining()){ // have header
          sizeHeader.flip();
          currentPacketSize = sizeHeader.getInt();
          if (inputBuffer.capacity() < currentPacketSize){
            inputBuffer = ByteBuffer.allocate(currentPacketSize);
          } else {
            inputBuffer.limit(currentPacketSize);
          }
        }
      } else {
        bytesRead = chan.read(inputBuffer);
        if (!inputBuffer.hasRemaining()) { // have packet
          // change from "writeten" state to "read" state
          inputBuffer.flip();
          for(Iterator i=listeners.iterator();i.hasNext();){
            ((NIOTCPConnectionListener)i.next()).packetReceived(
                this,inputBuffer.duplicate());
          }
          inputBuffer.clear();
          sizeHeader.clear();
          currentPacketSize = -1;
        }
      }
    } while(bytesRead>0);
    if (bytesRead == -1) { // closed
      disconnected();
    }
  }



  /**
   * connected
   */
  public void disconnected() {
    connected = false;
    try {
      chan.close();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
    for(Iterator i=listeners.iterator();i.hasNext();){
      ((NIOTCPConnectionListener)i.next()).disconnected(this);
    }

  }


  /**
   * addListener
   *
   * @param l NIOTCPConnectionListener
   */
  public void addListener(NIOTCPConnectionListener l) {
    listeners.add(l);

  }

  /**
   * send
   *
   * @param packet ByteBuffer
   */
  public synchronized void send(ByteBuffer packet) throws IOException {
    packet.flip();
    synchronized(outputHeader){
      outputHeader.clear();
      outputHeader.putInt(packet.remaining());
      outputHeader.flip();
      chan.write(outputHeader);
    }
    chan.write(packet);
  }

  /**
   * send
   *
   * @param packets ByteBuffer[]
   */
  public synchronized void send(ByteBuffer[] packets) throws IOException {
    int sz = 0;
    for(int i=0;i<packets.length;i++){
      packets[i].flip();
      sz += packets[i].remaining();
    }
    synchronized(outputHeader){
      outputHeader.clear();
      outputHeader.putInt(sz);
      outputHeader.flip();
      chan.write(outputHeader);
    }
    chan.write(packets);
  }

  /**
   * disconnect
   */
  public void disconnect() throws IOException {
    chan.close();
  }

}
