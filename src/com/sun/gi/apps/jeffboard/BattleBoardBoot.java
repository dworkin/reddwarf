/**
 *
 * <p>Title: BattleBoardBoot.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.apps.jeffboard;

import java.security.Principal;
import java.util.Set;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;

/**
 *
 * <p>Title: BattleBoardBoot.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class BattleBoardBoot implements SimBoot,SimUserListener{
	GLOReference currentlyFillingGame = null;
	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimBoot#boot(com.sun.gi.logic.GLOReference, boolean)
	 */
	public void boot(GLOReference thisGLO, boolean firstBoot) {
		if (firstBoot){ // GLO setup
			
		}
		SimTask task = SimTask.getCurrent();
		task.addUserListener(thisGLO);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserListener#userJoined(com.sun.gi.comm.routing.UserID, javax.security.auth.Subject)
	 */
	public void userJoined(UserID uid, Subject subject) {
		SimTask task = SimTask.getCurrent();
		Set<Principal> principles = subject.getPrincipals();
		Principal principal = principles.iterator().next();
		String playerName = principal.getName();
		String playerObjectName = "player_"+playerName;
		GLOReference playerRef = task.findGLO(playerObjectName);
		if (playerRef == null){
			BattleBoardPlayer playerTemplate = 
				new BattleBoardPlayer(playerName);
			playerRef = task.createGLO(playerTemplate,playerObjectName);
			if (playerRef == null){
				playerRef = task.findGLO(playerObjectName);
			}
		}
		task.addUserDataListener(uid,playerRef);
		BattleBoardGame game = getCurrentlyFillingGame(task);
		game.addPlayer(playerRef);
		if (game.isFull()){
			nextGame();
		}
	}

	/**
	 * 
	 */
	private void nextGame() {
		currentlyFillingGame = null;
		
	}

	/**
	 * @return
	 */
	private BattleBoardGame getCurrentlyFillingGame(SimTask task) {
		if (currentlyFillingGame==null){
			currentlyFillingGame = task.createGLO(new BattleBoardGame());
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserListener#userLeft(com.sun.gi.comm.routing.UserID)
	 */
	public void userLeft(UserID uid) {
		// TODO Auto-generated method stub
		
	}

}
