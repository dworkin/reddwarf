package com.sun.sgs.tutorial.server.swordworld;

import java.io.Serializable;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

public class SwordWorldPlayer implements ManagedObject, Serializable,
	ClientSessionListener {
	String name;
	private ClientSession currentSession=null;
	private ManagedReference currentRoom=null;
	
	public SwordWorldPlayer(String playerObjectName) {
		SwordWorld.logger.info("New Player Created: "+playerObjectName);
		name = playerObjectName;
	}
	
	public void disconnected(boolean graceful) {
		SwordWorld.logger.info("Player logged out: "+name);
		
	}
	
	public void setRoom(SwordWorldRoom room){
		DataManager dmgr = AppContext.getDataManager();
		currentRoom = dmgr.createReference(room);
		SwordWorld.logger.info("Player "+name+" room is "+room.getDescription());
	}
	
	public void setSession(ClientSession session){
		SwordWorld.logger.info("Session for player "+name+" is "+session);
		currentSession = session;
	}
	
	public void receivedMessage(byte[] message) {
		SwordWorld.logger.info("Player "+name+" received msg: "+message);
		String command  = new String(message);
		if (command.equalsIgnoreCase("look")){
			currentSession.send(
					currentRoom.get(SwordWorldRoom.class).look(this));
		}
	}

	public String getName() {
		return name;
	}

}
