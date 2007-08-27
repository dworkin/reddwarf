package com.sun.sgs.test.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Dummy identity manager for testing purposes.
 */
public class DummyIdentityManager implements IdentityManager {
    private final Map<String, IdentityInfo> identities =
	Collections.synchronizedMap(new HashMap<String, IdentityInfo>());
	
    public Identity authenticateIdentity(IdentityCredentials credentials) {
	DummyIdentity identity = new DummyIdentity(credentials);
	identities.put(identity.getName(), new IdentityInfo());
	return identity;
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

    /**
     * Identity returned by the DummyIdentityManager.
     */
    private static class DummyIdentity implements Identity, Serializable {

        private static final long serialVersionUID = 1L;
        private final String name;

        DummyIdentity(String name) {
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
	    getIdentityManager().setLoggedIn(name);
	}

        public void notifyLoggedOut() {
	    //System.err.println("notifyLoggedOut: " + name);
	    getIdentityManager().setLoggedOut(name);
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

    private static DummyIdentityManager getIdentityManager() {
	return (DummyIdentityManager)
	    AppContext.getManager(IdentityManager.class);
    }
}
