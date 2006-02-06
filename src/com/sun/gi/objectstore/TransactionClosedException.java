package com.sun.gi.objectstore;

/**
 * <p>Title: TransactionClosedException.java</p>
 * <p>Description: This exception is thrown if an operatio n is attempted  on a Transaction
 * after it has been comitted or aborted.  We *should* never see this exception</p>
 * <p>Copyright: Copyright (c) 2003 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc.</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

//@SuppressWarnings("serial")
public class TransactionClosedException extends RuntimeException {
  public TransactionClosedException() {
  }

}
