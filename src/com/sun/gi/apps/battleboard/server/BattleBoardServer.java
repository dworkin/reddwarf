/*
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package com.sun.gi.apps.battleboard.server;

import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;
import java.util.logging.Logger;
import javax.security.auth.Subject;

/**
 * The BattleBoard server. <p>
 *
 * There's not much to this class: 
 */
public class BattleBoardServer
	implements SimBoot<BattleBoardServer>, SimUserListener
{
    private static final long serialVersionUID = -6452053898871581484L;
    
    private static Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    // SimBoot methods

    /**
     * Boots the BattleBoard application.  <p>
     *
     * Invoked by the SGS stack when BattleBoard is booted.  If
     * <code>firstBoot</code> is true, this call represents the first
     * time that this method has been called for this application
     * across all stacks for the current instance of the app server. 
     * When this is true, additional intialization may be necessary. 
     * Otherwise, this app has been booted already and is simply being
     * brought up in a new stack.
     *
     * @param thisGLO a GLOReference to this server itself
     *
     * @param firstBoot <code>true</code> if this is the first
     * instance of this app to be created, <code>false</code>
     * otherwise
     */
    public void boot(GLOReference<? extends BattleBoardServer> thisGLO,
	    boolean firstBoot)
    {
	SimTask task = SimTask.getCurrent();

	log.info("Booting BattleBoard Server as appID " + task.getAppID());

	/*
	 * firstBoot is true if and only if this is the first time the
	 * boot method has been called for this app on this system (or
	 * if evidence that the app has already been started has been
	 * removed -- for example if someone removes all the data
	 * associated with this app from the ObjectStore).  Therefore
	 * when firstBoot is true, we do all the initialization.
	 *
	 * For this app, initialization is very simple:  all we need
	 * to do is to create the matchmaker.
	 */

	if (firstBoot) {
	    Matchmaker.create();
	}

        /*
	 * Register this object as the handler for login and
	 * disconnect events for all users on this app.
	 */

	task.addUserListener(thisGLO);
    }

    // SimUserListener methods

    /**
     * {@inheritDoc}
     */
    public void userJoined(UserID uid, Subject subject) {
	Player.userJoined(uid, subject);
    }

    /**
     * {@inheritDoc}
     */
    public void userLeft(UserID uid) {
	Player.userLeft(uid);
    }
}
