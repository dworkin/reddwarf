/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

/**
 * <p>Title: Room.java</p>
 * <p>Description: </p>
 *
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
