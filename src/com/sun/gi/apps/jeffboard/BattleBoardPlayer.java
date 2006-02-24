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
public class BattleBoardPlayer implements SimUserDataListener{
	String playerName;
	long wins=0;
	long losses=0;
	/**
	 * @param playerName
	 */
	public BattleBoardPlayer(String playerName) {
		this.playerName = playerName;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userDataReceived(com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void userDataReceived(UserID from, ByteBuffer data) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userJoinedChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
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
	public void dataArrivedFromChannel(ChannelID id, UserID from, ByteBuffer buff) {
		// TODO Auto-generated method stub
		
	}

}
