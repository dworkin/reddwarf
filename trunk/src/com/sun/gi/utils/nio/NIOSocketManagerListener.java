package com.sun.gi.utils.nio;

public interface NIOSocketManagerListener {
  public void newTCPConnection(NIOTCPConnection connection);
  public void connected(NIOTCPConnection connection);
  public void connectionFailed(NIOTCPConnection connection);
}
