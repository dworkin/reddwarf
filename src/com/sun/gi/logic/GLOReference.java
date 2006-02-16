package com.sun.gi.logic;

/**
 * <p>Title: GLOReference</p>
 * <p>Description: A reference to a Game Logic Object (GLO)
 * The name GLOReference is historical and should probably be changed at some
 * point to GLOReference.
 *
 * All GLOs must refer to other GLOs through GLOReferences.  This allows
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
   * an in memory instantiation of it.  Multiple get() calls on GLOReferences
   * that reference the same GLO will only cause one lock and will all return
   * the same instance.
   * 
   * @param task SimTask The SimTask context
   * @return The in memory instance of the GLO
   */
  public GLO get(SimTask task);
  /**
   * This method is like a get() except that the object is not write locked.
   * It returns a task-local copy that will not get stored back to the
   * objectstore.  Multiple calls to peek() on GLOReferences that reference the
   * same GLO will return the same task-local copy
   * 
   * @param task SimTask  The SimTask context
   * @return The task local instance.
   */
  public GLO peek(SimTask task);
  
  
  /**
   * This method is like a get() except that it does not block.
   * Instead, if the object is already locked, it simply returns null
   * objectstore.  Multiple calls to peek() on GLOReferences that reference the
   * same GLO will return the same task-local copy
   * 
   * @param task SimTask  The SimTask context
   * @return The in memory instance of the GLO or null.
  */
 public GLO attempt(SimTask task);

  /**
   * This method makes a copy of an GLOReference that references the same
   * GLO.
   * @return GLOReference the new GLOReference.
   */
  public GLOReference shallowCopy();
/**
 * @param task
 */
	public void delete(SimTask task);

}
