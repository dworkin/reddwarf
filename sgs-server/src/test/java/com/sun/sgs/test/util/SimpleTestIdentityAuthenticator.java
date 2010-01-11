/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --
 */
package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.security.auth.login.LoginException;

/**
 *  A simple identity authenticator for testing.
 *
 */
public class SimpleTestIdentityAuthenticator implements IdentityAuthenticator {
    // All the identities in the system, on all nodes
    public static final SystemIdentities allIdentities = 
            new SystemIdentities();

    /**
     * Creates a new instance of SimpleTestIdentityAuthenticator
     */
    public SimpleTestIdentityAuthenticator(Properties properties) {

    }

    /**
     * {@inheritDoc}
     */
    public Identity authenticateIdentity(IdentityCredentials credentials) 
        throws LoginException 
    {
        DummyIdentity identity = new DummyIdentity(credentials);
        SimpleTestIdentityAuthenticator.allIdentities.put(identity.getName());
	return identity;
    }

    /**
     * {@inheritDoc}
     */
    public String[] getSupportedCredentialTypes() {
        return new String [] { NamePasswordCredentials.TYPE_IDENTIFIER };
    }

    public static class SystemIdentities {
        // All the identities in the system, on all nodes
        private final Map<String, IdentityInfo> identities =
            Collections.synchronizedMap(new HashMap<String, IdentityInfo>());

        public void put(String name) {
            identities.put(name, new IdentityInfo());
        }

        private void setLoggedIn(String name) {
            IdentityInfo info = identities.get(name);
            synchronized (info.loggedInLock) {
                info.loggedIn = true;
                info.loggedInLock.notifyAll();
            }
        }

        private void setLoggedOut(String name) {
            IdentityInfo info = identities.get(name);
            synchronized (info.loggedOutLock) {
                info.loggedOut = true;
                info.loggedOutLock.notifyAll();
            }
        }

        public boolean getNotifyLoggedIn(String name) {
            IdentityInfo info = identities.get(name);
            if (info == null) {
                return false;
            }
            synchronized (info.loggedInLock) {
                if (info.loggedIn != true) {
                    try {
                        info.loggedInLock.wait(500);
                    } catch (InterruptedException e) {
                    }
                }
                return info.loggedIn;
            }
        }
	
        public boolean getNotifyLoggedOut(String name) {
            IdentityInfo info = identities.get(name);
            if (info == null) {
                return false;
            }
            synchronized (info.loggedOutLock) {
                if (info.loggedOut != true) {
                    try {
                        info.loggedOutLock.wait(500);
                    } catch (InterruptedException e) {
                    }
                }
                return info.loggedOut;
            }
        }

        private static class IdentityInfo {
            private final Object loggedInLock = new Object();
            private final Object loggedOutLock = new Object();
            private boolean loggedIn = false;
            private boolean loggedOut = false;
        }
    }
	


    /**
     * Identity returned by the SimpleTestIdentityAuthenticator.
     */
    public static class DummyIdentity implements Identity, Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;

        public DummyIdentity(String name) {
            this.name = name;
        }

        DummyIdentity(IdentityCredentials credentials) {
            this.name = ((NamePasswordCredentials) credentials).getName();
        }

        public String getName() {
            return name;
        }

        public void notifyLoggedIn() {
	    //System.err.println("notifyLoggedIn: " + name);
	    SimpleTestIdentityAuthenticator.allIdentities.setLoggedIn(name);
	}

        public void notifyLoggedOut() {
	    //System.err.println("notifyLoggedOut: " + name);
	    SimpleTestIdentityAuthenticator.allIdentities.setLoggedOut(name);
	}

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (! (o instanceof DummyIdentity))
                return false;
            return ((DummyIdentity) o).name.equals(name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
	
        @Override
        public String toString() {
            return DummyIdentity.class.getName() + "[" + name + "]";
        }
    }
}
