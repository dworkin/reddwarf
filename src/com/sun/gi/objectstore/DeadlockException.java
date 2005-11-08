package com.sun.gi.objectstore;

/**
 * <p>Title: DeadlockException.java</p>
 * <p>Description: This exception is thrown when a GET attempt would result in
 * a potential deadlock.</p>
 * <p><b>A Darkstar app must NEVER catch this exception!!</p></b>
 * <p>Copyright: Copyright (c) 2003 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc.</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

@SuppressWarnings("serial")
public class DeadlockException extends RuntimeException {
    public DeadlockException() {
      super();
    }

  public DeadlockException(String string) {
    super(string);
  }
}