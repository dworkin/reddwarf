package com.sun.sgs.service;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionException;
import java.io.Serializable;

/**
 * Provides facilities for services to manage access to shared, persistent
 * objects.  In addition to the methods provided by {@link DataManager}, this
 * interface includes methods for an additional set of service bindings.  These
 * service bindings are intended to by used by services to support their needs
 * to store objects persistently.  The objects associated with service bindings
 * are independent of the objects bound through methods defined on
 * <code>DataManager</code>.  Services should insure that they use unique names
 * for service bindings by using their class or package name as the prefix for
 * the name.
 */
public interface DataService extends DataManager, Service {

    /**
     * Obtains the object associated with the service binding of a name.
     * Callers need to notify the system before modifying the object or any of
     * the non-managed objects it refers to by calling {@link #markForUpdate
     * markForUpdate} or {@link ManagedReference#getForUpdate
     * ManagedReference.getForUpdate} before making the modifications.
     *
     * @param	<T> the type of the object
     * @param	name the name
     * @param	type a class representing the type of the object
     * @return	the object associated with the service binding of the name
     * @throws	ClassCastException if the object bound to the name is not of
     *		the specified type
     * @throws	NameNotBoundException if no object is bound to the name
     * @throws	ObjectNotFoundException if the object bound to the name is not
     *		found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    <T> T getServiceBinding(String name, Class<T> type);

    /**
     * Specifies an object for the service binding of a name, replacing any
     * previous binding.  The object, as well as any objects it refers to, must
     * implement {@link Serializable}.  Note that this method will throw {@link
     * IllegalArgumentException} if <code>object</code> does not implement
     * <code>Serializable</code>, but is not guaranteed to check that all
     * referred to objects implement <code>Serializable</code>.  Any instances
     * of {@link ManagedObject} that <code>object</code> refers to directly, or
     * indirectly through non-managed objects, need to be referred to through
     * instances of {@link ManagedReference}.
     *
     * @param	name the name
     * @param	object the object associated with the service binding of the
     *		name
     * @throws	IllegalArgumentException if <code>object</code> does not
     *		implement {@link Serializable}
     * @throws	ObjectNotFoundException if the object has been removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void setServiceBinding(String name, ManagedObject object);

    /**
     * Removes the service binding for a name.  Note that the object previously
     * bound to the name, if any, is not removed; only the binding between the
     * name and the object is removed.  To remove the object, use the {@link
     * #removeObject removeObject} method.
     *
     * @param	name the name
     * @throws	NameNotBoundException if the name is not bound
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	#removeObject removeObject
     */
    void removeServiceBinding(String name);
}
