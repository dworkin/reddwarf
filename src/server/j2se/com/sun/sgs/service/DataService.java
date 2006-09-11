
package com.sun.sgs.service;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;


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
     * @param object the object to manage
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(T object);

    /**
     * Tells the service to manage this object. The object is associated
     * with the given name for future searching.
     *
     * @param object the object to manage
     * @param objectName the name of the object
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(T object, String objectName);

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
            findManagedObject(String objectName);

    /**
     * Locks the referenced object and returns the associated value.
     *
     * @param reference the object's reference
     *
     * @return the object
     */
    public <T extends ManagedObject> T get(ManagedReference<T> reference);

    /**
     * Returns the referenced object for read-only access without locking it.
     *
     * @param reference the object's reference
     *
     * @return the object
     */
    public <T extends ManagedObject> T peek(ManagedReference<T> reference);

    /**
     * Destroys the referenced object.
     *
     * @param reference a reference to the object to destroy
     */
    public void destroyManagedObject(
            ManagedReference<? extends ManagedObject> reference);

}
