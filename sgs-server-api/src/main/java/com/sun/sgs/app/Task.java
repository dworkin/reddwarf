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
