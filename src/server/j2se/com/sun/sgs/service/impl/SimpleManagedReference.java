
package com.sun.sgs.service.impl;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.ManagedReference;

import com.sun.sgs.service.DataService;


/**
 * This is a simple implementation of <code>ManagedReference</code> that
 * is used by <code>SimpleDataService</code>. It maintains no intenral
 * state, and simply passes all access to its service.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleManagedReference<T extends ManagedObject>
    implements ManagedReference<T>, ManagedObject
{

    // the referenced object's identifier
    private long objectId;

    // the service that manages the referenced object
    private DataService dataService;

    // whether or not the referenced object has been deleted
    private boolean deleted;

    /**
     * Creates an instance of <code>SimpleManagedReference</code>.
     *
     * @param objectId the referenced object's identifier
     * @param dataService the <code>DataService</code> that manages
     *                    the referenced object
     */
    public SimpleManagedReference(long objectId, DataService dataService) {
        this.objectId = objectId;
        this.dataService = dataService;

        deleted = false;
    }

    /**
     * Returns the referenced object's identifier.
     *
     * @return the object identifier
     */
    public long getObjectId() {
        return objectId;
    }

    /**
     * Acquires an exclusive lock on the referenced object and returns the
     * object for any use.
     *
     * @return the managed object
     */
    public T get() {
        if (deleted)
            return null;

        return dataService.get(this);
    }

    /**
     * Returns a non-consistant view of the referenced object. The object
     * may not be modified, as any changes will not be reflected in the
     * managed state.
     *
     * @return the managed object
     */
    public T peek() {
        if (deleted)
            return null;

        return dataService.peek(this);
    }

    /**
     * Deletes the referenced object.
     */
    public void delete() {
        if (deleted)
            return;

        deleted = true;
        dataService.destroyManagedObject(this);
    }

    /**
     * Returns the hash code for this reference, which is simply the
     * object identifier.
     *
     * @return the reference's hash code
     */
    public int hashCode() {
        return (int)objectId;
    }

    /**
     * Returns true if the other object references the same object as
     * this reference, false otherwise.
     *
     * @param other a <code>SimpleManagedReference</code>
     *
     * @return true if the references are the same, false otherwise
     */
    public boolean equals(Object other) {
        if (other == null)
            return false;

        if (! (other instanceof SimpleManagedReference))
            return false;

        return (((SimpleManagedReference)other).objectId == objectId);
    }

}
