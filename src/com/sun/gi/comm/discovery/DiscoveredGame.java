package com.sun.gi.comm.discovery;

public interface DiscoveredGame {
  public String getName();
  public int getId();
  public DiscoveredUserManager[] getUserManagers();
}
