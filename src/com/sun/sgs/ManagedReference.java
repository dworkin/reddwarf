
package com.sun.sgs;

import com.sun.sgs.ManagedObject;


/**
 * This is the core interface used to reference all data objects managed
 * by the system. It is used to access any objects that are shared or
 * otherwise managed by server. Individual <code>Service</code>s are left
 * free to implement their own logic as needed.
 * <p>
 * FIXME: We are going to re-visit this interface and see if we want to
 * change the ways that objects are accessed.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ManagedReference<T extends ManagedObject>
{

    /**
     * Acquires an exclusive lock on the referenced object and returns the
     * object for any use.
     *
     * @return the managed object
     */
    public T get();

    /**
     * Returns a non-consistant view of the referenced object. The object
     * may not be modified, as any changes will not be reflected in the
     * managed state.
     *
     * @return the managed object
     */
    public T peek();

    /**
     * Deletes the referenced object.
     */
    public void delete();

}
