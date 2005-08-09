package com.sun.gi.comm.discovery;

import java.util.Map;

public interface Advertisement {
  public void setHeader(int gameID, String gameName,
                                 String gameDescription);
  public void addUserManager(String clientClassName);
  public void addParameter(String name, String value);
  public void finish();

}
