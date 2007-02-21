package com.sun.sgs.tutorial.server.swordworld;



import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

public class SwordWorldRoom implements ManagedObject, Serializable {
	List<ManagedReference> inventory = new LinkedList<ManagedReference>();
	List<ManagedReference> players = new LinkedList<ManagedReference>();
	private String description;
	public SwordWorldRoom(String description){
		this.description = description;
	}
	
	/**
	 * This method adds an object to the room's inventory
	 * @param object
	 */
	public void putObject(SwordWorldObject object) {
		SwordWorld.logger.info("Object "+object.getDescription()+
				" placed in " +description);
		/**
		 * Note that we cant save the obejct itself in our list 
		 * or we would end up with a local copy.  Instead  we
		 * save a ManagedReference to that managed object in our
		 * inventory list.
		 */
		DataManager mgr= AppContext.getDataManager();
		inventory.add(mgr.createReference(object));
		
	}

	public void playerEnters(SwordWorldPlayer player) {
		SwordWorld.logger.info("Player "+player.getName()+" enters "+
				description);
		DataManager mgr= AppContext.getDataManager();
		players.add(mgr.createReference(player));
		player.setRoom(this);
	}

	public byte[] look(SwordWorldPlayer player) {
		SwordWorld.logger.info("Player "+player.getName()+" looks at "+
				description);
		DataManager dmgr = AppContext.getDataManager();
		StringBuffer output = new StringBuffer();
		output.append("You are  in "+description+".\n");
		List<SwordWorldPlayer> playerList = listPlayers();
		playerList.remove(player);
		if (!playerList.isEmpty()){
			output.append("Also in here are ");
			Iterator<SwordWorldPlayer> iter = playerList.iterator();
			SwordWorldPlayer other =  iter.next();
			output.append(other.getName());
			if (playerList.size()>1){
				other = iter.next();
				while (iter.hasNext()){
					output.append(" ,"+other.getName());
					other = iter.next();
				}			
				output.append(" and "+other.getName());
			}
			output.append(".\n");
		}
		if (inventory.size()>0){
			output.append("On the floor you see:\n");
			for(ManagedReference objectRef : inventory){
				output.append(
						objectRef.get(SwordWorldObject.class).getDescription()+
						"\n");
			}
		}
		return new String(output).getBytes();
	}

	

	private List<SwordWorldPlayer> listPlayers() {
		List<SwordWorldPlayer> playerList = new LinkedList<SwordWorldPlayer>();
		for(ManagedReference ref : players){
			playerList.add(ref.get(SwordWorldPlayer.class));
		}
		return playerList;
	}

	public String getDescription() {
		return description;
	}
}
