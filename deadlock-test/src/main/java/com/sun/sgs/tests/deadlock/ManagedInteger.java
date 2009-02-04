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

import java.util.Random;
import java.io.Serializable;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.AppContext;

/**
 * A toy {@code ManagedObject} that keeps track of a single integer value.
 */
public class ManagedInteger implements Serializable, ManagedObject {
    
    /** 
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1;
    
    private static final int MAX = 100;

    private final Random random;
    private int value;
    private int sum;
    
    private int updates;

    public ManagedInteger() {
        random = new Random();
        value = random.nextInt(MAX);
        sum = 0;
        updates = 0;
    }
    
    /**
     * Returns the value of this object.
     * @return
     */
    public int getValue() {
        return value;
    }
    
    /**
     * Sets the internal value of this object to a random integer between
     * {@code 0} and {@value MAX}.
     */
    public void setValue() {
        AppContext.getDataManager().markForUpdate(this);
        value = random.nextInt(MAX);
        updates++;
    }
    
    /**
     * Set the sum 
     * 
     * @param sum
     */
    public void setSum(int sum) {
        AppContext.getDataManager().markForUpdate(this);
        this.sum = sum;
    }
    
    /**
     * Returns the number of times this object's value has been updated
     * since it was created.
     * 
     * @return the number of updates since creation
     */
    public int getUpdates() {
        return updates;
    }
    
}
