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

package com.sun.sgs.internal;

/**
 * Provides a pluggable mechanism for replacing the {@link ManagerLocator}
 * used by the application to get Managers from the Project Darkstar stack
 * through the {@link com.sun.sgs.app.AppContext AppContext}.  This class
 * should not be instantiated.
 */
public final class InternalContext {
    
    // the current locator for this context
    private static volatile ManagerLocator managerLocator;
    
    /** This class should not be instantiated. */
    private InternalContext() { }
    
    /**
     * Returns the {@code ManagerLocator} for use by the current
     * application.  This method is used by the 
     * {@link com.sun.sgs.app.AppContext AppContext} to retrieve Managers
     * from the Project Darkstar stack.  Generally, it should not need to be 
     * called by an application.
     *
     * @return	the {@code ManagerLocator} for the current application
     * @throws	IllegalStateException if the {@code ManagerLocator}
     *          is uninitialized
     */
    public static ManagerLocator getManagerLocator() {
        ManagerLocator locator = managerLocator;
        if (locator == null) {
            throw new IllegalStateException("ManagerLocator is not set");
        }
        return locator;
    }
    
    /**
     * Sets the {@code ManagerLocator} which is used to retrieve
     * managers for the application.  <p>
     * 
     * In most situations, this method
     * should only be called once upon bootup of a Project Darkstar
     * container.  It is also useful for swapping out implementations
     * of the Project Darkstar stack for testing purposes.
     * Typically, an application should never have a reason
     * to call this method, and doing so could cause unexpected
     * results. <p>
     * 
     * Specifying {@code null} for {@code managerLocator} sets the 
     * {@code ManagerLocator} back to its original, uninitialized state.
     * 
     * @param managerLocator the {@code ManagerLocator} that the 
     *        {@code InternalContext} should use to retrieve managers
     *        or {@code null} to make it uninitialized
     */
    public static synchronized void 
            setManagerLocator(ManagerLocator managerLocator) {
        InternalContext.managerLocator = managerLocator;
    }
}
