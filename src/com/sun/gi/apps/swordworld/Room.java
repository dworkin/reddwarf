/**
 *
 * <p>Title: Room.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.apps.swordworld;

import java.util.LinkedList;
import java.util.List;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

/**
 *
 * <p>Title: Room.java</p>
 * <p>Description: </p>
 * <p>This is the Game Logic Class (GLC) that defines Room Game Logic
 * Objects (GLOs) in our toy MUD example.
 * <p>In this toy eample a room is simply a container that has
 * a description, a list of items in the room, and a list of players
 * in the room.
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class Room implements GLO {
	// This holds the description of the room itself.
	private String description;
	// This holds the list of items in the room.
	// All items are GLOs that are instances of the GLC "RoomObject"
	private List<GLOReference<RoomObject>> inventory = 
		new LinkedList<GLOReference<RoomObject>>();
	//This holds the list of players in the room.
	//All players are GLOs that are instances of the GLC "Player"
	private List<GLOReference<Player>> players = 
		new LinkedList<GLOReference<Player>>();
	/**
	 * @param string
	 */
	public Room(String string) {
		description = string;
	}
	
	/**
	 * This is the one command rooms currently support.
	 * It returns a text string that describes the contents of the
	 * Room.
	 * It takes as a parameter a GLOReference to the Player asking
	 * for the list and does not put them in the list of "others
	 * in the room".
	 * @param meRef  A GLOReference to the player looking at the room
	 * @return
	 */
	public String getDescription(GLOReference<Player> meRef){
		// we will need a SimTask so get the current one
		SimTask simTask = SimTask.getCurrent();
		// Initialize the string with the description of the room
		String out = "You are in "+description;
		// add either a list of the descriptions off the RoomObjects
		// referred to by the GLOReferences in the inventory list,
		// or a stement that there are none if the lsit is empty.
		out+=" containing";
		if (inventory.isEmpty()){
			out+=" nothing.";
		} else {
			out+=":\n";
			// loop through all the GLOReferences in the inventory list
			for (GLOReference<RoomObject> objectRef : inventory){
				// for each reference, get the RoomObject GLO
				RoomObject objectGLO = objectRef.get(simTask);
				// add the descrption from that GLO
				// to the string
				out += objectGLO.getDescription()+"\n";
			}
		}
		// Now we do the same thing with the Player GLOReferences
		// in the player list, with the exception that we elimiate
		// the looking player from the string.
		if (players.size()==1){
			// only one player here, must be us.
			out+="You are alone";
		} else {
			out+="With you in the room are:\n";
			// loop through the GLORefrences in the players list.
			for(GLOReference<Player> playerRef : players){
				// if the GLOReference in the list is equal to
				// the GLOreference passed in, then it is a 
				// reference to the "looker" and we skip it
				if (!playerRef.equals(meRef)){
					// otherwise we get the Player GLO
					Player playerGLO = playerRef.get(simTask);
					// and then add its name to the string.
					out+=playerGLO.getName()+"\n";
				}
			}
		}
		return out;
	}
	/**
	 * This method simply adds a GLOReference to a RoomOBject GLO
	 * into the inventory list.
	 * @param swordRef
	 */
	public void addToInventory(GLOReference<RoomObject> swordRef) {
		inventory .add(swordRef);
		
	}
	/**
	 * This method simply adds a GLOReference to a Player GLO
	 * into the players list.  It is called by the SwordWorldBoot GLO
	 * when a player logs into the system. 
	 * @param playerRef
	 */
	public void addPlayer(GLOReference<Player> playerRef) {
		players.add(playerRef);
		SimTask simTask = SimTask.getCurrent();
		Player player = playerRef.get(simTask);
		try {
			player.setCurrentRoom(simTask.makeReference(this));
		} catch (InstantiationException e) {
			System.out.println("Failed to create a this-reference");
			e.printStackTrace();
		}
		
	}
	/**
	 * * This method simply removes a GLOReference to a Player GLO
	 * into the players list.  It is called by the SwordWorldBoot
	 * GLO when a player logs out or disconnects from the system.
	 * @param playerRef
	 */
	public void removePlayer(GLOReference<Player> playerRef) {
		players.remove(playerRef);
		SimTask simTask = SimTask.getCurrent();
		Player player = playerRef.get(simTask);
		player.setCurrentRoom(null);			
	}

}
