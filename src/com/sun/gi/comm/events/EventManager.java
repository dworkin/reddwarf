package com.sun.gi.comm.events;

public interface EventManager {
  public void setServerTimerEvent(long time,byte[] data, boolean repeat);
}
