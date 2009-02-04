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

package com.sun.sgs.tests.deadlock;

import java.io.Serializable;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

/**
 * A self rescheduling task that sums up the values of the given list
 * of {@link ManagedInteger}s and sets this value on the single associated
 * {@link ManagedInteger} object.
 */
public class SumTask implements Task, Serializable {
    
    private final ManagedReference<ManagedInteger>[] integers;
    private final ManagedReference<ManagedInteger> update;
    
    public SumTask(ManagedReference<ManagedInteger>[] integers,
                   ManagedReference<ManagedInteger> update) {
        this.integers = integers;
        this.update = update;
    }

    @Override
    public void run() throws Exception {
        int sum = 0;
        for (ManagedReference<ManagedInteger> i : integers) {
            sum += i.get().getValue();
        }
        update.getForUpdate().setSum(sum);
        update.getForUpdate().setValue();
        
        AppContext.getTaskManager().scheduleTask(this);
    }

}
