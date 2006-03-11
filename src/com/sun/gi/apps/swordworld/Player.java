
package com.sun.gi.apps.swordworld;

import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;

/**
 *
 * <p>Title: Player.java</p>
 * <p>Description: </p>
 * <p>This class is the Game Logic Class that defines Player-GLOs
 * in the SwordWorld demo.</p>  
 * <p>Player-GLOs are a very important concept in the SGS.  They
 * act like the user's "body" in the world of the GLOs.  They receieve
 * commands from the client program and based on those commands they
 * act on and change other GLOs.</p>
 * <p>In order to be a Player-GLO, a GLO's defining  GLC
 * must implement the SimUserDataListener event and the GLO must
 * be registered as the SimUserDataListener for that user's session.
 * <p>See SwordWorldBoot.userJoined() to see how registration of
 * a SimUserDataListener is accomplished.</p>
 * @see SwordWorldBoot.userJoined() 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class Player implements SimUserDataListener {
	// The name of the player, used by Room to
	// help create the room description.
	String name;
	// A GLOReference to our current Room.  This is
	// so we can be removed from the room when we leave.
	GLOReference<Room> currentRoomRef;
	// In the current version of the API, the only way for
	// the server to send data back down to the client is through
	// a channel.  (this is likely to chnage in future versions)
	// For now, both the client and the SwordWorldBoot open the
	// channel "GAMECHANNEL".  This is a field to store the
	// ChannelID that SwordWorldBoot was given for that channel.
	private ChannelID appChannel;
	/**
	 * @param string
	 */
	public Player(String string,ChannelID cid) {
		name = string;
		appChannel = cid;
		// TODO Auto-generated constructor stub
	}

	// This is called to set what room we think we are 
	// currently in.
	public void setCurrentRoom(GLOReference<Room> room){
		currentRoomRef = room;
	}
	/**
	 * All GLOs should define a dumym serialVersionUID.  This
	 * turns off version checking so we can change the class
	 * and still load old data that might already be in the ObjectStore.
	 */
	private static final long serialVersionUID = 1L;
	/* *
	 * This is the callback that is called when data arrives at the
	 * server from the user we registered ourselves as the 
	 * SimUserDataListener for.
	 * 
	 * @see com.sun.gi.logic.SimUserDataListener#userDataReceived(com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void userDataReceived(UserID from, ByteBuffer data) {
		// in this app, all the data are string commands
		// so we read all the bytes from the buffer and turn
		// them back into a string
		byte[] inbytes = new byte[data.remaining()];
		data.get(inbytes);
		String commandString = new String(inbytes).trim();
		// this is just a debug printf to show whatcommand we recieved
		System.out.println("CommandString ="+commandString);
		// we split the string up into indvidual words
		String[] words = StringUtils.explode(commandString," ");
		// we are going to need a simTask, so we get the current
		// one here.
		SimTask simTask = SimTask.getCurrent();
		// word[0] is the basic command.  The only command
		// we know so far is "look"
		if (words[0].equalsIgnoreCase("look")){
			// we get the actual Room GLO for the
			// room we are currently in.
			Room roomGLO = currentRoomRef.get(simTask);
			try {
				// the getDescription command requires we pass in a
				// GLOReference to us so it knows who not to put in
				// the description.
				// Sicne we dont have a GLOReference to ourselves
				// handy, we ask the SimTask to make us
				// one
				GLOReference<Player> myRef = simTask.makeReference(this);
				// now we ask the Room GLO for the description.
				String out = roomGLO.getDescription(myRef);
				// We take the string description and write
				// its byets to a ByteBuffer so we can send it back
				// to the client.
				ByteBuffer outbuff = ByteBuffer.allocate(out.length());
				outbuff.put(out.getBytes());
				// we ask the SimTask to send the data to the client
				// who we received the command from.
				// We send it on the "GAMECHANNEL" channel that
				// SwordWorldBoot opened and told us about.
				// We want gauranteed delivery of the message,
				// so we set the reliable delivery flag to true.
				simTask.sendData(appChannel,from,outbuff,true);
			} catch (InstantiationException e) {
				// We *should* never see this exception.
				// If we did then somethign went very wrong,
				// like perhapse we asked the SimTask to make
				// a reference to a GLO that this task had not
				// yet acquired.
				System.out.println("Failed to create this-reference");
				e.printStackTrace();
			}
		}
		
	}

	/* *
	 * A callabck telling us when the user who we are registered as
	 * the SimUserDataListener for joins a channel.  We dont need
	 * that information in this app so we ignore it.
	 * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userJoinedChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * A callabck telling us when the user who we are registered as
	 * the SimUserDataListener for leaves a channel.  We dont need
	 * that information in this app so we ignore it.
	 * @see com.sun.gi.logic.SimUserDataListener#userLeftChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userLeftChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * The server can "snoop" channels by using the SimTask to tell the
	 * SGS to call this method every time data is sent by a particualr user on
	 * a particualr channel.
	 * We dont need this functionality so we leave this callback empty.
	 * @see com.sun.gi.logic.SimUserDataListener#dataArrivedFromChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void dataArrivedFromChannel(ChannelID cid, UserID from, ByteBuffer buff) {
		
	}

	/**
	 * This method returns our player name. Its used when others
	 * ask the Room GLO to describe the room.
	 * @return our player name
	 */
	public String getName() {

		return name;
	}

}
