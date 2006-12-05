package com.sun.sgs.app;

/**
 * Provides an interface that {@link ManagedObject} classes can implement to
 * control how changes to the state of their instances are detected.  For
 * classes that implement this interface, the {@link #getChangesState
 * getChangesState} method will be called when the object is first obtained
 * from the {@link DataManager}, and again at the end of a transaction.  The
 * results of serializing the two objects will be compared, and the associated
 * managed object will only be saved to the <code>DataManager</code> if the
 * serialized forms differ.  For managed objects that do not implement this
 * interface, the serialized form of the object itself will be used to
 * determine if the object has changed.  Note that no comparison is performed
 * if the object is marked modified explicitly by calls to {@link
 * DataManager#markForUpdate DataManager.markForUpdate} or {@link
 * ManagedReference#getForUpdate ManagedReference.getForUpdate}. <p>
 *
 * Classes that call <code>DataManager.markForUpdate</code> or
 * <code>ManagedReference.getForUpdate</code> whenever their state, or the
 * state of any non-managed objects they refer to, changes can implement this
 * interface to return a constant value with a small serialized form &mdash;
 * <code>null</code> works well for this purpose.  The fact that the serialized
 * form of this value does not change is an efficient way to tell the
 * <code>DataManager</code> that the object does not need to be saved. <p>
 *
 * Another use case for this interface is for classes that need to perform some
 * work to determine if it has been changed in the current transaction, but a
 * smaller amount of work than would be needed to serialize the entire instance
 * and the objects it refers to.  In that case, the class can implement
 * <code>getChangesState</code> to return an object that represents the current
 * modification state of the object.
 */
public interface DetectChanges {

    /**
     * Returns an object whose serialized state represents modifications to any
     * state of this instance and any non-managed objects to which this
     * instance refers.
     *
     * @return	an object whose serialized state represents modifications to
     *		the state of this instance
     */
    Object getChangesState();
}
