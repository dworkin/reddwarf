package com.sun.gi.objectstore;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DeadlockException extends RuntimeException {
    public DeadlockException() {
      super();
    }

  public DeadlockException(String string) {
    super(string);
  }
}