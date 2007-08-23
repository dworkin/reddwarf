/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
