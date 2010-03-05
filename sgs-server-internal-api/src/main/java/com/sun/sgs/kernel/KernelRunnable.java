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

package com.sun.sgs.kernel;


/**
 * This is the base interface used for all tasks that can be submitted
 * to instances of <code>Scheduler</code>.
 */
public interface KernelRunnable {

    /**
     * Returns the fully qualified type of the base task that is run by this
     * <code>KernelRunnable</code>. Many types of runnables wrap around other
     * instances of <code>KernelRunnable</code> or <code>Task</code>. This
     * method provides the type of the base task that is being wrapped by any
     * number of <code>KernelRunnable</code>s, where a given task that wraps
     * another task will return that other task's base type such that any
     * wrapping task can be queried and will return the same base task type.
     *
     * @return the fully-qualified name of the base task class type
     */
    String getBaseTaskType();

    /**
     * Runs this <code>KernelRunnable</code>. If this is run by a
     * <code>Scheduler</code> that support re-try logic, and if an
     * <code>Exception</code> is thrown that implements 
     * <code>ExceptionRetryStatus</code> then the <code>Scheduler</code>
     * will consult the <code>shouldRetry</code> method of the
     * <code>Exception</code> to see if this task should be re-run.
     *
     * @throws Exception if any error occurs
     */
    void run() throws Exception;

}
