/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.util;

import com.sun.sgs.kernel.ComponentRegistry;

/**
 * A factory for creating collections whose key/value pairs are stored as
 * service bindings.  This factory may be obtained from the service {@link
 * ComponentRegistry}. 
 */
public interface BindingKeyedCollection {

    /**
     * Returns the key prefix for this collection.
     *
     * @return	the key prefix for this collection
     */
    String getKeyPrefix();

    /**
     * Adds a key start.
     */
    void addKeyStart();

    /**
     * Adds a key stop.
     */
    void addKeyStop();

    /**
     * Removes the key start.
     */
    void removeKeyStart();
    
    /**
     * Removes the key stop.
     */
    void removeKeyStop();
}

