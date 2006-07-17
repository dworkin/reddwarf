
/*
 * DataManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Jul 10, 2006	12:25:46 AM
 * Desc: 
 *
 */

package com.sun.sgs.manager;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.ManagedReference;

import com.sun.sgs.service.Transaction;


/**
 * This manager provides access to the data-related routines.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class DataManager
{

    /**
     * Returns an instance of <code>DataManager</code>.
     *
     * @return an instance of <code>DataManager</code>
     */
    public static DataManager getInstance() {
        // FIXME: return the instance
        return null;
    }

    /**
     * Private helper that resolves the transaction from the thread.
     */
    private Transaction getTransaction() {
        return ((TaskThread)(Thread.currentThread())).
            getTask().getTransaction();
    }

    /**
     * Tells the manager to manage this object. The object has no name
     * associated with it.
     *
     * @param object the object to manage
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(T object) {
        Transaction txn = getTransaction();
        return txn.getDataService().manageObject(txn, object);
    }

    /**
     * Tells the manager to manage this object. The object is associated
     * with the given name for future searching.
     *
     * @param object the object to manage
     * @param objectName the name of the object
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(T object, String objectName) {
        Transaction txn = getTransaction();
        return txn.getDataService().manageObject(txn, object);
    }

    /**
     * Tries to find an already managed object based on that object's
     * name. If no object can be found with the given name then null
     * is returned.
     *
     * @param objectName the name of the object
     *
     * @return a reference to the object, or null
     */
    public ManagedReference<? extends ManagedObject>
            findManagedObject(String objectName) {
        Transaction txn = getTransaction();
        return txn.getDataService().findManagedObject(txn, objectName);
    }

    /**
     * Destroys the referenced object.
     *
     * @param reference a reference to the object to destroy
     */
    public <T extends ManagedObject>
            void destroyManagedObject(ManagedReference<T> reference) {
        //return getService().destroyGLO(reference);
    }

}
