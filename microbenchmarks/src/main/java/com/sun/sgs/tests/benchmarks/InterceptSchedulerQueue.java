/*
 * Copyright (c) 2007-2009, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.tests.benchmarks;

import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.impl.kernel.schedule.FIFOSchedulerQueue;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 */
public class InterceptSchedulerQueue extends FIFOSchedulerQueue {

    static InterceptSchedulerQueue theQueue;
    
    // separate queue to store tasks scheduled by the benchmark
    private LinkedBlockingQueue<ScheduledTask> queue;

    public InterceptSchedulerQueue(Properties p) {
        super(p);
        queue = new LinkedBlockingQueue<ScheduledTask>();
        theQueue = this;
    }

    public ScheduledTask getNextBenchmarkTask(boolean wait)
            throws InterruptedException {
        ScheduledTask task = queue.poll();
        if ((task != null) || (!wait)) {
            return task;
        }
        return queue.take();
    }

    public void addTask(ScheduledTask task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }

        if (task.getTask().getBaseTaskType().equals(
                "com.sun.sgs.tests.benchmarks.BenchmarkService$SpecialTask")) {
            addBenchmarkTask(task);
        } else {
            super.addTask(task);
        }
    }

    public void addBenchmarkTask(ScheduledTask task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }

        if (!queue.offer(task)) {
            throw new TaskRejectedException("Request was rejected");
        }
    }

    public void clear() {
        queue.clear();
    }

}
