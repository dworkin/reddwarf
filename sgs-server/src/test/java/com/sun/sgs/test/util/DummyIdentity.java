/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import java.io.Serializable;

/** Provides a simple implementation of Identity used for testing. */
public class DummyIdentity implements Identity, Serializable {

    private static final long serialVersionUID = 1;

    private final String name;
    /** Creates an instance of this class. */
    public DummyIdentity() { 
        name = "Me!";
    }

    public DummyIdentity(String name) {
        this.name = name;
    }
    /** -- Implement Identity -- */

    public String getName() { return name; }

    public void notifyLoggedIn() { }

    public void notifyLoggedOut() { }

    public String toString() {  return name;  }
    
    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if ((o == null) || (! (o instanceof DummyIdentity)))
            return false;

        return ((DummyIdentity)o).name.equals(name);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return name.hashCode();
    }
}
