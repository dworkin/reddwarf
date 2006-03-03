/**
 *
 * <p>Title: BattleBoardBoot.java</p>
 * 
 */

    /*****************************************************************************
     * Copyright (c) 2006 Sun Microsystems, Inc.  All Rights Reserved.
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions are met:
     *
     * - Redistribution of source code must retain the above copyright notice,
     *   this list of conditions and the following disclaimer.
     *
     * - Redistribution in binary form must reproduce the above copyright notice,
     *   this list of conditions and the following disclaimer in the documentation
     *   and/or other materails provided with the distribution.
     *
     * Neither the name Sun Microsystems, Inc. or the names of the contributors
     * may be used to endorse or promote products derived from this software
     * without specific prior written permission.
     *
     * This software is provided "AS IS," without a warranty of any kind.
     * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
     * ANY IMPLIED WARRANT OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
     * NON-INFRINGEMEN, ARE HEREBY EXCLUDED.  SUN MICROSYSTEMS, INC. ("SUN") AND
     * ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS
     * A RESULT OF USING, MODIFYING OR DESTRIBUTING THIS SOFTWARE OR ITS
     * DERIVATIVES.  IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
     * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
     * INCIDENTAL OR PUNITIVE DAMAGES.  HOWEVER CAUSED AND REGARDLESS OF THE THEORY
     * OF LIABILITY, ARISING OUT OF THE USE OF OUR INABILITY TO USE THIS SOFTWARE,
     * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
     *
     * You acknowledge that this software is not designed or intended for us in
     * the design, construction, operation or maintenance of any nuclear facility
     *
     *****************************************************************************/

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
	private int guestCount=0;
	private Map<UserID,GLOReference> guestMap = 
		new HashMap<UserID,GLOReference>();
	
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
		GLOReference playerRef; 
		if (playerName.equalsIgnoreCase("Guest")){
			String guestName = "Guest"+guestCount;
			guestCount++;	
			BattleBoardPlayer playerTemplate = 
				new BattleBoardGuest(guestName,
						getCurrentlyFillingGameGLORef(task));
			playerRef = task.createGLO(playerTemplate,null);
			guestMap.put(uid,playerRef);
		} else {
			String playerObjectName = "player_"+playerName;
			playerRef = task.findGLO(playerObjectName);
			if (playerRef == null){
				BattleBoardPlayer playerTemplate = 
					new BattleBoardPlayer(playerName,
						getCurrentlyFillingGameGLORef(task));
				playerRef = task.createGLO(playerTemplate,playerObjectName);
				if (playerRef == null){
					playerRef = task.findGLO(playerObjectName);
				}
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
		if (guestMap.containsKey(uid)){
			guestMap.get(uid).delete(SimTask.getCurrent());
			guestMap.remove(uid);
		}
		playerToGameMap.remove(uid);		
	}

}
