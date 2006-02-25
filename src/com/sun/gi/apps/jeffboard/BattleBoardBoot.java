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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
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
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	GLOReference currentlyFillingGame = null;
	ChannelID controlChannel;
	long gameCounter=0;
	Map<UserID,GLOReference> playerToGameMap = 
		new HashMap<UserID, GLOReference>();
	
	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimBoot#boot(com.sun.gi.logic.GLOReference, boolean)
	 */
	public void boot(GLOReference thisGLO, boolean firstBoot) {
		if (firstBoot){ // GLO setup
			
		}
		SimTask task = SimTask.getCurrent();
		task.addUserListener(thisGLO);
		controlChannel = task.openChannel("matchmaker");
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
				new BattleBoardPlayer(playerName,
						getCurrentlyFillingGameGLORef(task));
			playerRef = task.createGLO(playerTemplate,playerObjectName);
			if (playerRef == null){
				playerRef = task.findGLO(playerObjectName);
			}
		}
		task.addUserDataListener(uid,playerRef);
		BattleBoardGame game = getCurrentlyFillingGame(task);
		game.addPlayer(playerRef,uid);
		playerToGameMap.put(uid,getCurrentlyFillingGameGLORef(task));
		if (game.isFull()){
			nextGame();
		}
		task.join(uid,controlChannel);
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
	private GLOReference getCurrentlyFillingGameGLORef(SimTask task) {
		if (currentlyFillingGame==null){
			currentlyFillingGame = task.createGLO(
				new BattleBoardGame(task,controlChannel,
						"BattleBoard"+gameCounter++));
		}
		return currentlyFillingGame;
	}
	
	private BattleBoardGame getCurrentlyFillingGame(SimTask task){
		return (BattleBoardGame)
			getCurrentlyFillingGameGLORef(task).get(task);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserListener#userLeft(com.sun.gi.comm.routing.UserID)
	 */
	public void userLeft(UserID uid) {
		GLOReference gameRef = playerToGameMap.get(uid);
		BattleBoardGame game = 
			(BattleBoardGame)gameRef.get(SimTask.getCurrent());
		game.withdraw(uid);
		playerToGameMap.remove(uid);		
	}

}
