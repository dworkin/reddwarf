
package com.sun.sgs.service;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.ManagedReference;


/**
 * This type of <code>Service</code> handles access to managed objects.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface DataService extends Service
{

    /**
     * Tells the service to manage this object. The object has no name
     * associated with it.
     *
     * @param txn the <code>Transaction</code> state
     * @param object the object to manage
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(Transaction txn, T object);

    /**
     * Tells the service to manage this object. The object is associated
     * with the given name for future searching.
     *
     * @param txn the <code>Transaction</code> state
     * @param object the object to manage
     * @param objectName the name of the object
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(Transaction txn, T object,
                                             String objectName);

    /**
     * Tries to find an already managed object based on that object's
     * name. If no object can be found with the given name then null
     * is returned.
     *
     * @param txn the <code>Transaction</code> state
     * @param objectName the name of the object
     *
     * @return a reference to the object, or null
     */
    public ManagedReference<? extends ManagedObject>
            findManagedObject(Transaction txn, String objectName);

    /**
     * Destroys the referenced object.
     *
     * @param txn the <code>Transaction</code> state
     * @param reference a reference to the object to destroy
     */
    public void destroyManagedObject(Transaction txn,
            ManagedReference<? extends ManagedObject> reference);

}
