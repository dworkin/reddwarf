package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Represents a reference to a managed object.  Classes that implement
 * <code>ManagedReference</code> must also implement {@link Serializable}.
 * Applications should create instances of this interface using the {@link
 * DataManager#createReference DataManager.createReference} method.  These
 * <code>ManagedReference</code> instances should be used to store references
 * to instances of <code>ManagedObject</code> referred to by other managed
 * objects or by the non-managed objects they refer to. <p>
 *
 * Applications should not use instances of <code>ManagedReference</code> as
 * the values of static fields or in other locations not managed by the
 * <code>DataManager</code>.  There is no guarantee that objects only reachable
 * through these external references will continue to be managed by the
 * <code>DataManager</code>.
 *
 * @param	<T> the type of the referenced object
 * @see		DataManager#createReference DataManager.createReference
 */
public interface ManagedReference<T extends ManagedObject> {

    /**
     * Obtains the object associated with this reference.  Applications need to
     * notify the system before modifying the returned object or any of the
     * non-managed objects it refers to by calling {@link #getForUpdate
     * getForUpdate} or {@link DataManager#markForUpdate
     * DataManager.markForUpdate} before making the modifications.
     *
     * @return	the associated object
     * @throws	ObjectNotFoundException if the object associated with this
     *		reference is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	#getForUpdate getForUpdate
     * @see	DataManager#markForUpdate DataManager.markForUpdate
     */
    T get();

    /**
     * Obtains the managed object associated with this reference, and notifies
     * the system that the object is going to be modified.
     *
     * @return	the associated object
     * @throws	ObjectNotFoundException if the object associated with this
     *		reference is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	DataManager#markForUpdate DataManager.markForUpdate
     */
    T getForUpdate();

    /**
     * Compares the specified object with this reference.  Returns
     * <code>true</code> if the argument is a <code>ManagedReference</code>
     * that refers to the same object as this reference, otherwise
     * <code>false</code>.
     *
     * @param	object the object to be compared with
     * @return	if <code>object</code> refers to the same object
     */
    boolean equals(Object object);

    /**
     * Returns an appropriate hash code value for the object.
     *
     * @return	the hash code for this object
     */
    int hashCode();
}
