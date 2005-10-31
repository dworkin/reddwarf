package com.sun.gi.comm.users.client.impl;

import java.util.ArrayList;
import java.util.List;

import com.sun.gi.comm.discovery.DiscoveredUserManager;
import com.sun.gi.comm.discovery.Discoverer;
import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.users.client.*;

/**
 * 
 * <p>Title: DefaultUserManagerPolicy
 * <p>Description: This class implements a simple stochastic choice of UserManager instances.</p>
 * <p>Copyright: Copyright (c) Oct 24, 2005 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */

public class DefaultUserManagerPolicy implements UserManagerPolicy{
	/**
	 *  Default constructor 
	 *
	 */
	
  public DefaultUserManagerPolicy() {
  }

  
  
  /**
   * This method is called by the ClientConnectionManager to choose a specific
   * instance of a UserManager to connect to.
   * 
   * @param game The structure returned from Discovery that describes a game
   * including details on all available connection points.
   * @param umanagerName The FQCN of the desired userManager.
   * @return A DiscoveredUserManager structure describing the chosen user manager 
   * or a null if there are no valid candidates.
   */
  
  public DiscoveredUserManager choose(DiscoveredGame game,
                                      String umanagerName) {
    DiscoveredUserManager[] mgrs = game.getUserManagers();
    List<DiscoveredUserManager> umgrs = new ArrayList<DiscoveredUserManager>();
    for(DiscoveredUserManager mgr: mgrs){
    	if (mgr.getClientClass().equals(umanagerName)){
    		umgrs.add(mgr);
    	}
    }
    if (umgrs.size()>0){
    	return (DiscoveredUserManager)umgrs.get((int)(Math.random()*umgrs.size()));
    } else {
    	return null;
    }
  }

}
