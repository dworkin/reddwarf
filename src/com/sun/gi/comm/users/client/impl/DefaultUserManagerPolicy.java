package com.sun.gi.comm.users.client.impl;

import java.util.ArrayList;
import java.util.List;

import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.discovery.DiscoveredUserManager;
import com.sun.gi.comm.users.client.UserManagerPolicy;

/**
 * <p>Title: DefaultUserManagerPolicy
 * <p>Description: This class implements a simple stochastic choice
 * of UserManager instances.</p>
 * <p>Copyright: Copyright (c) Oct 24, 2005 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems</p>
 *
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public class DefaultUserManagerPolicy implements UserManagerPolicy {

    public DefaultUserManagerPolicy() { }

    /**
     * Called by the ClientConnectionManager to choose a specific
     * instance of a UserManager to connect to.
     *
     * @param game  The structure returned from Discovery that describes a game
     *		    including details on all available connection points.
     *
     * @param umanagerName The FQCN of the desired userManager.
     *
     * @return  a DiscoveredUserManager structure describing the
     *		chosen user manager, or null if there are no
     *          valid candidates.
     */
    public DiscoveredUserManager choose(DiscoveredGame game,
	    String umanagerName) {

	DiscoveredUserManager[] mgrs = game.getUserManagers();
	List<DiscoveredUserManager> umgrs =
	    new ArrayList<DiscoveredUserManager>();

	for (DiscoveredUserManager mgr : mgrs){
	    if (mgr.getClientClass().equals(umanagerName)) {
		umgrs.add(mgr);
	    }
	}

	return umgrs.isEmpty()
		? null
		: umgrs.get((int)(Math.random() * umgrs.size()));
    }
}
