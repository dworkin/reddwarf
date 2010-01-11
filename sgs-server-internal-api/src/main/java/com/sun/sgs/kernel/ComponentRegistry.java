/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.kernel;

import java.util.MissingResourceException;


/**
 * This is a general registry interface used to provide access to a collection
 * of components by type. It is used by the kernel during startup to
 * configure system components and <code>Service</code>s.
 */
public interface ComponentRegistry extends Iterable<Object>
{

    /**
     * Returns a component from the registry, matched based on the given
     * type. If there are no matches, or there is more than one possible
     * match, then an exception is thrown.
     *
     * @param <T> the type of the component
     * @param type the <code>Class</code> of the requested component
     *
     * @return the requested component
     *
     * @throws MissingResourceException if the requested component is not
     *                                  available, or if there is more
     *                                  than one matching component
     */
    <T> T getComponent(Class<T> type);

}
