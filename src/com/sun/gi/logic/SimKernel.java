package com.sun.gi.logic;

import com.sun.gi.comm.routing.*;
import com.sun.gi.objectstore.*;

/**
 * <p>Title: Sim Kernel</p>
 * <p>Description: This is the interface to the logic engine.  Each
 * game (simulation) in a slice has its own run-time sim object that
 *  implements this interface and provides the operating context for
 *  the game.</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface SimKernel {
  /**
   * Creates a transactional context used to access GLOs
   * @param appID long The ID of the game creating the transaction
   * @param loader ClassLoader The ClassLoader to use to load
   * the classes of deserialized objects.
   * @return Transaction An object that implemenst the Transaction interface.
   */
  public Transaction newTransaction(long appID, ClassLoader loader);

  /**
   * Queues a task for execution by the kernel.
   * @param simTask SimTask The task to be executed.
   */
  public void queueTask(SimTask simTask);
  /**
   * Gets a thread to use for the execution of a SimTask
   * @return SimThread The thread.
   */
  public SimThread getSimThread();
  /**
   * Returns the ObjectStore in use by this game.
   * @return ObjectStore The Objectstore.
   */
  public ObjectStore getOstore();
  /**
   * This method creates a new user in the back end and returns a USerID
   * representing it.  It can be used by back end logic to create "virtual users."
   * @return UserID The ID of the new users.
   */
  public UserID createUser();

  /**
   * Sends data to a list of userIDs
   *
   * @param targets UserID[]  the IDs to send the data to
   * @param from UserID the userID the data came from (return address)
   * @param bs byte[] the data to transmit
   */
  public void sendData(UserID[] targets, UserID from, byte[] bs);
}
