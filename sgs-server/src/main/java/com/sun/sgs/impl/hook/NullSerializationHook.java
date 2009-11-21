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
 */

package com.sun.sgs.impl.hook;

/**
 * SerializationHook which does nothing.
 */
public class NullSerializationHook implements SerializationHook {

    /**
     * Returns the parameter unmodified.
     *
     * @param topLevelObject the top level object.
     * @param object         the object.
     * @return the same object.
     */
    public Object replaceObject(Object topLevelObject, Object object) {
        return object;
    }

    /**
     * Returns the parameter unmodified.
     *
     * @param object the object.
     * @return the same object.
     */
    public Object resolveObject(Object object) {
        return object;
    }
}
