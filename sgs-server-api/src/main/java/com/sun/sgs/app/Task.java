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

import java.io.Serializable;

/**
 * Defines an application operation that will be run by the {@link
 * TaskManager}.  Classes that implement <code>Task</code> must also implement
 * {@link Serializable}.
 *
 * @see		TaskManager
 */
public interface Task {

    /**
     * Performs an action, throwing an exception if the action fails.
     *
     * @throws	Exception if the action fails
     */
    void run() throws Exception;
}
