
package com.sun.sgs.impl.manager;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.app.DataManager;

import com.sun.sgs.service.DataService;


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

    // the backing data service
    private DataService dataService;

    /**
     * Creates an instance of <code>SimpleDataManager</code>.
     *
     * @param dataService the backing service
     */
    public SimpleDataManager(DataService dataService) {
        super();

        this.dataService = dataService;
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
        return dataService.manageObject(object);
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
        return dataService.manageObject(object, objectName);
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
        return dataService.findManagedObject(objectName);
    }

}
