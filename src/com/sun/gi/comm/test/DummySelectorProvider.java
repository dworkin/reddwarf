package com.sun.gi.comm.test;

import java.nio.channels.spi.SelectorProvider;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;

public class DummySelectorProvider extends SelectorProvider{
  public DummySelectorProvider() {
    try {
      throw new InstantiationException("This shoudl not be instantiated");
    } catch (Exception e){
      e.printStackTrace();
      System.exit(-1);
    }
  }

  /**
   * openDatagramChannel
   *
   * @return DatagramChannel
   */
  public DatagramChannel openDatagramChannel() {
    return null;
  }

  /**
   * openPipe
   *
   * @return Pipe
   */
  public Pipe openPipe() {
    return null;
  }

  /**
   * openServerSocketChannel
   *
   * @return ServerSocketChannel
   */
  public ServerSocketChannel openServerSocketChannel() {
    return null;
  }

  /**
   * openSocketChannel
   *
   * @return SocketChannel
   */
  public SocketChannel openSocketChannel() {
    return null;
  }

  /**
   * openSelector
   *
   * @return AbstractSelector
   */
  public AbstractSelector openSelector() {
    return null;
  }
}
