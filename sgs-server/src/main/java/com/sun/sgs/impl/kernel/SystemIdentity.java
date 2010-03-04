/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.auth.IdentityImpl;

/**
 * The system identity is pinned to the node it was created on and
 * is not used for load balancing decisions.
 */
public class SystemIdentity extends IdentityImpl {

    private static final long serialVersionUID = 1L;
    
    /**
     * Creates an instance of {@code SystemIdentity} associated with the
     * given name.
     *
     * @param name the name of this identity
     */
    public SystemIdentity(String name) {
        super(name);
    }
}
