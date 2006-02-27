/**
 *
 * <p>Title: BattleBoardPlayer.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
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
		String[] words = explode(cmd);
		BattleBoardGame game = 
			(BattleBoardGame)myGame.get(SimTask.getCurrent());
		if (words[0].equalsIgnoreCase("join")){
			game.setScreenName(from,words[1]);
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
		byte[] inputBytes = new byte[buff.remaining()];
		buff.get(inputBytes);
		String cmd = new String(inputBytes);
		System.out.println("CMD: "+playerName+": "+cmd);
		String[] words = explode(cmd);
		BattleBoardGame game = 
			(BattleBoardGame)myGame.get(SimTask.getCurrent());
		if (words[0].equalsIgnoreCase("withdraw")){
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
