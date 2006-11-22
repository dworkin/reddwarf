package com.sun.sgs.app;

/**
 * Provides an interface that {@link ManagedObject} subclasses can implement in
 * order to control the way changes to the state of their instances is
 * detected. <p>
 *
 * Managed objects that do not implement this interface will be serialized when
 * first accessed and again at commit time to determine if their contents have
 * changed. <p>
 *
 * Classes that always make sure to call {@link DataManager#markForUpdate
 * DataManager.markForUpdate} and {@link ManagedReference#getForUpdate
 * ManagedReference.getForUpdate} whenever their state, or the state of any
 * non-managed objects they refer to, changes can choose to implement this
 * interface by returning a constant value with a small serialized form &mdash;
 * <code>null</code> works well for this purpose.  The fact that the serialized
 * form of this value does not change will tell the {@link DataManager} that
 * the object does not need to be saved. <p>
 *
 * Another possibility is that a class still needs to perform some work to
 * determine if it has been changed in the current transaction, but it is a
 * smaller amount of work than serializing the entire instance and the objects
 * it refers to.  In that case, the class can implement {@link #getChangesState
 * getChangesState} to return an object that represents the current
 * modification of the object.
 */
public interface DetectChanges {

    /**
     * Returns an object whose serialized state represents any state of this
     * instance, or any non-managed objects this instance refers to, that may
     * have been modified.
     *
     * @param	an object whose serialized state represents the state of this
     *		instance
     */
    Object getChangesState();
}
	
