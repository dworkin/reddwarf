/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
