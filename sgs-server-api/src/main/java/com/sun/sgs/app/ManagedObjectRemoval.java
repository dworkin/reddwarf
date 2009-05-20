/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
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
