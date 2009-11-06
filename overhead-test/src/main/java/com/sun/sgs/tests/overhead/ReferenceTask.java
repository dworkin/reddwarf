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

package com.sun.sgs.tests.overhead;

import com.sun.sgs.app.Task;
import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.util.ScalableHashSet;
import java.io.Serializable;
import java.util.Iterator;

/**
 *
 */
public class ReferenceTask implements Task, Serializable {

    private final int accesses;
    private final ManagedReference<ScalableHashSet<MutableObject>> set;
    private final int setSize = 1000;

    private Iterator<MutableObject> iterator;

    public ReferenceTask(int accesses) {
        this.accesses = accesses;

        ScalableHashSet<MutableObject> actualSet =
                                       new ScalableHashSet<MutableObject>();
        for (int i = 0; i < setSize; i++) {
            actualSet.add(new MutableObject());
        }
        
        this.iterator = actualSet.iterator();
        this.set = AppContext.getDataManager().createReference(actualSet);
    }

    public void run() {
        int access = 0;
        while (access < accesses) {
            if (!iterator.hasNext()) {
                iterator = set.get().iterator();
            }

            iterator.next().update();
            access++;
        }

        AppContext.getTaskManager().scheduleTask(this);
    }

    private static class MutableObject implements ManagedObject, Serializable {
        private int value = 0;

        public void update() {
            value++;
        }
    }

}
