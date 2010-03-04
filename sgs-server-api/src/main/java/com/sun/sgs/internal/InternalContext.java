/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
