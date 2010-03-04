/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.service.task;

import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;

/**
 * This interface is used to define a pluggable continuation policy for
 * {@link TaskService} implementations when the
 * {@link TaskService#shouldContinue()} method is called.
 *
 * All implementations must define a constructor of the form
 * ({@link Properties}, {@link ComponentRegistry}, {@link TransactionProxy})
 */
public interface ContinuePolicy {

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
