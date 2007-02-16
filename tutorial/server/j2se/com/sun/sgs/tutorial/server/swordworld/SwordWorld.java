package com.sun.sgs.tutorial.server.swordworld;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

public class SwordWorld implements AppListener, Serializable {
	static Logger logger = 
		Logger.getLogger("sgs.tutorial.server");
	ManagedReference roomRef;
	
	public void initialize(Properties props) {
		logger.info("Initializing SwordWorld");
		//CREATE ROOM MANAGEDOBJECT
		SwordWorldRoom room = new SwordWorldRoom("A non-descript room");
		// CREATE SWORD MANAGEDOBJECT
		SwordWorldObject sword = new SwordWorldObject("A shiney sword.");
		//ADD REF TO SWORD MANAGEDOBJECT TO ROOM'S INVENTORY
		room.putObject(sword);
		// SAVE ROOM FOR LATER REFERENCE
		// NOTE THAT THIS ACTULLAY STARTS MANAGEMENT OF THE ROOM OBJECT
		DataManager dmgr = AppContext.getDataManager();
		roomRef =dmgr.createReference(room);
		logger.info("SwordWorld Initialized");
	}

	public ClientSessionListener loggedIn(ClientSession session) {
		logger.info("SwordWorld Client arrived: "+session.getName());
		// ManagedObject_name = “player_”+ SESSION.PLAYER_NAME;
		String playerObjectName = "player_"+session.getName();
		// try to find player object, if non existant then create
		DataManager dmgr = AppContext.getDataManager();
		SwordWorldPlayer player = null;
		try {
			player =  
				dmgr.getBinding(playerObjectName, SwordWorldPlayer.class);
			roomRef.getForUpdate(SwordWorldRoom.class).playerEnters(player);
		} catch (NameNotBoundException ex){	
			// this is a new player
			player = new SwordWorldPlayer(playerObjectName);
			dmgr.setBinding(playerObjectName, player);
			// place player in room
			roomRef.getForUpdate(SwordWorldRoom.class).playerEnters(player);
		} catch (Exception ex){
			// other exceptions should not happen. 
			// notify, log thrown exception and quit
			logger.info("Exception on AppListener.loggedIn: "+ex.getMessage());
			logger.throwing(this.getClass().getName(), "loggedIn", ex);
			return null;
		}
		// player object will need current session to communicate
		// with client
		player.setSession(session);
		// return player object as listener to this client session
		return player;
	}

}
