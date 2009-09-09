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

package com.sun.sgs.impl.service.task;

import com.sun.sgs.app.TaskManager;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;

/**
 * This interface is used to define a continuation policy for the
 * {@link TaskServiceImpl} when the {@link TaskServiceImpl#shouldContinue()}
 * method is called.
 *
 * All implementations must define a constructor of the form
 * ({@link Properties}, {@link ComponentRegistry}, {@link TransactionProxy})
 */
interface ContinuePolicy {

    /**
     * Returns {@code true} if the currently running task should do more work
     * if it is available.  Otherwise, returns {@code false}.  This method
     * should only be called from a transactional context.
     *
     * @return {@code true} if the currently running task should do more work
     *         if possible; otherwise {@code false}
     * @throws TransactionException if the operation failed because of a
     *	       problem with the current transaction
     * @see TaskManager#shouldContinue() TaskManager.shouldContinue()
     */
    boolean shouldContinue();

}
