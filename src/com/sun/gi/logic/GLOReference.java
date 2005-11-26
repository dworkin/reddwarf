package com.sun.gi.logic;

import java.io.Serializable;

/**
 * <p>Title: SOReference</p>
 * <p>Description: A reference to a Game Logic Object (GLO)
 * The name SOReference is historical and should probably be changed at some
 * point to GLOReference.
 *
 * All GLOs must refer to other GLOs through SOReferences.  This allows
 * the persistance mechanisms to work correctly.  If a GLO has a normal java
 * reference to another GLO as a field then that second GLO's state becoems
 * part of the state of the first GLO as oppsoed to an independant object
 * in the objectstore.</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface GLOReference {
  /**
   * This method locks the referenced GLO for write and returns a reference to
   * an in memory instantiation of it.  Multiple get() calls on SOReferences
   * that reference the same GLO will only cause one lock and will all return the
   * same instance.
   * @param task SimTask The SimTask context
   * @return Serializable The in memory instance of the GLO
   */
  public Serializable get(SimTask task);
  /**
   * This method is like a get() except that the object is not write locked.
   * It returns a task-local copy that will not get stored back to the
   * objectstore.  Multiple calls to peek() on SOReferences that reference the
   * same GLO will return the same task-local copy
   * @param task SimTask  The SimTask context
   * @return Serializable The task local instance.
   */
  public Serializable peek(SimTask task);

  /**
   * This method makes a copy of an SOReference that references the same
   * GLO.
   * @return SOReference the new SOReference.
   */
  public GLOReference shallowCopy();
}
