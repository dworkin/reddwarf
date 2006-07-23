
package com.sun.sgs.manager.impl;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.ManagedReference;

import com.sun.sgs.kernel.TransactionProxy;

import com.sun.sgs.manager.DataManager;

import com.sun.sgs.service.Transaction;


/**
 * This is a simple implementation of <code>DataManager</code> that is the
 * default used.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleDataManager extends DataManager
{

    // the proxy used to access transaction state
    private TransactionProxy transactionProxy;

    /**
     * Creates an instance of <code>SimpleDataManager</code>.
     *
     * @param transactionProxy the proxy used to access transaction state
     */
    public SimpleDataManager(TransactionProxy transactionProxy) {
        super();

        this.transactionProxy = transactionProxy;
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
        Transaction txn = transactionProxy.getCurrentTransaction();
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
        Transaction txn = transactionProxy.getCurrentTransaction();
        return txn.getDataService().manageObject(txn, object, objectName);
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
        Transaction txn = transactionProxy.getCurrentTransaction();
        return txn.getDataService().findManagedObject(txn, objectName);
    }

}
