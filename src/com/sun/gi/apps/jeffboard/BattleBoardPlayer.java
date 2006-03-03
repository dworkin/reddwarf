/**
 *
 * <p>Title: BattleBoardPlayer.java</p>
 * <p>Description: </p>
 * @author Jeff Kesselman
 * @version 1.0
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

package com.sun.gi.apps.jeffboard;

import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;

/**
 *
 * <p>Title: BattleBoardPlayer.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class BattleBoardPlayer implements GLO, SimUserDataListener{
	String playerName;
	long wins=0;
	long losses=0;
	GLOReference myGame;
	/**
	 * @param playerName
	 */
	public BattleBoardPlayer(String playerName, 
			GLOReference myGame) {
		this.playerName = playerName;
		this.myGame = myGame;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userDataReceived(com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void userDataReceived(UserID from, ByteBuffer buff) {
		byte[] inputBytes = new byte[buff.remaining()];
		buff.get(inputBytes);
		String cmd = new String(inputBytes);
		System.out.println("CMD: "+playerName+": "+cmd);
		String[] words = explode(cmd);
		BattleBoardGame game = 
			(BattleBoardGame)myGame.get(SimTask.getCurrent());
		if (words[0].equalsIgnoreCase("join")){
			game.setScreenName(from,words[1]);
		} else if (words[0].equalsIgnoreCase("withdraw")){
			game.withdraw(from);
		} else if (words[0].equalsIgnoreCase("move")){
			game.makeMove(from, words[1],Integer.parseInt(words[2]),
					Integer.parseInt(words[3]));
		} else if (words[0].equalsIgnoreCase("pass")){
			game.passMove(from);
		} else {
			System.err.println("BB Player Error: Unknown command: "+cmd);
		}
		
		
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userJoinedChannel(ChannelID cid, UserID uid) {
		BattleBoardGame game = 
			(BattleBoardGame)myGame.get(SimTask.getCurrent());
		game.joinedChannel(uid,cid);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userLeftChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userLeftChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#dataArrivedFromChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void dataArrivedFromChannel(ChannelID cid, UserID from, ByteBuffer buff) {
		
		
	}

	/**
	 * @param cmd
	 * @return
	 */
	private String[] explode(String cmd) {
		return StringExploder.explode(cmd);
	}

	/**
	 * @return
	 */
	public boolean isAlive() {
		// TODO Auto-generated method stub
		return false;
	}
	
	public void gameOver(boolean winner){
		if (winner){
			wins++;
		} else {
			losses++;
		}
	}

}
