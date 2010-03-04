/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.app;

/**
 * An interface that managed objects should implement in order to be notified
 * when they are being removed from the {@link DataManager}.  When the {@link
 * DataManager#removeObject DataManager.removeObject} method is called on an
 * object that implements this interface, that method will first call {@link
 * #removingObject removingObject} before removing the object from the data
 * manager.  Managed objects containing references to other managed objects
 * that should be removed when the main object is removed can implement the
 * {@code removingObject} method to remove those referred-to objects.  The
 * {@code removingObject} method will not be called if this object has already
 * been removed or is not currently managed by the data manager. <p>
 *
 * Note that the implementation of {@code removingObject} should make sure that
 * it only removes objects that are logically "owned" by the main object, and
 * that objects are not removed more than once.  In particular, the
 * implementation should not call {@code DataManager.removeObject} on the main
 * object itself.
 *
 * @see DataManager#removeObject DataManager.removeObject
 */
public interface ManagedObjectRemoval extends ManagedObject {

    /**
     * Performs additional operations that are needed when this object is
     * removed.
     */
    void removingObject();
}
