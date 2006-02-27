package com.sun.gi.logic;

/**
 * <p>Title: GLOReference</p>
 * <p>Description: A reference to a Game Logic Object (GLO)
 *
 * All GLOs must refer to other GLOs through GLOReferences.  This allows the
 * persistance mechanisms to work correctly.  If a GLO has a normal java
 * reference to another GLO as a field then that second GLO's state becoems
 * part of the state of the first GLO as oppsoed to an independant object in
 * the objectstore.</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface GLOReference<T extends GLO> {

    /**
     * Locks the referenced GLO for write and returns a reference to
     * an in memory instantiation of it.  Multiple get() calls on
     * GLOReferences that reference the same GLO will only cause one
     * lock and will all return the same instance.
     * 
     * @param task The SimTask context
     *
     * @return the in-memory instance of the GLO
     */
    public T get(SimTask task);

    /**
     * Returns a task-local copy of the referenced GLO that will not
     * get stored back to the objectstore.  Peek is like get() except
     * that the object is not write locked.  Multiple calls to peek()
     * on GLOReferences that reference the same GLO will return the
     * same task-local copy.
     *
     * @param task The SimTask context
     *
     * @return the task-local instance.
     */
    public T peek(SimTask task);

    /**
     * A non-blocking version of get().  If the object is not locked,
     * it is locked and returned as in get().  If the object is
     * already locked, it returns null.  Multiple calls to attempt() on
     * GLOReferences that reference the same GLO will return the same
     * task-local copy.
     * 
     * @param task The SimTask context
     *
     * @return The in-memory instance of the GLO, or null.
     */
    public T attempt(SimTask task);

    /**
     * This method makes a copy of an GLOReference that references the same
     * GLO.
     *
     * @return GLOReference the new GLOReference.
     */
    public GLOReference<T> shallowCopy();

    /**
     * Removes the associated GLO from the objectstore, destroying all
     * persistence data for it.
     * 
     * @param task The SimTask context
     */
    public void delete(SimTask task);
}
