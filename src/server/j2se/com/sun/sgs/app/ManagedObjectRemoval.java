/*
 * Copyright 2007 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.app;

/**
 * An interface that managed objects should implement if they want to be
 * notified that the managed object is being removed from the {@link
 * DataManager}.  When the {@link DataManager#removeObject
 * DataManager.removeObject} method is called on an object that implements this
 * interface, the method will first call {@link #removingObject removingObject}
 * before removing the object from the data manager.  Classes that contain
 * references to other managed objects that should be removed when the
 * referring object is removed can implement the {@code removingObject} method
 * to insure that the referred to objects are also removed.  Note that {@code
 * removingObject} should not calling {@code DataManager.removeObject} on the
 * current object.
 */
public interface ManagedObjectRemoval extends ManagedObject {

    /**
     * Called when this object is being removed from the {@link DataManager}.
     */
    void removingObject();
}
