package com.sun.gi.comm.users.client;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2005</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ClientAlreadyConnectedException extends Exception {
  public ClientAlreadyConnectedException() {
  }

  /**
   * ClientAlreadyConnectedException
   *
   * @param string String
   */
  public ClientAlreadyConnectedException(String string) {
    super(string);
  }

}
