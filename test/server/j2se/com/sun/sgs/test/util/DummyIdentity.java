/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import java.io.Serializable;

/** Provides a simple implementation of Identity used for testing. */
public class DummyIdentity implements Identity, Serializable {

    private static final long serialVersionUID = 1;

    /** Creates an instance of this class. */
    public DummyIdentity() { }

    /** -- Implement Identity -- */

    public String getName() { return "Me!"; }

    public void notifyLoggedIn() { }

    public void notifyLoggedOut() { }

}
