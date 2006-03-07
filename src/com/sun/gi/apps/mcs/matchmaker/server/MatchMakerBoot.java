package com.sun.gi.apps.mcs.matchmaker.server;

import java.io.IOException;
import java.net.URL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.utils.ReversableMap;
import com.sun.gi.utils.SGSUUID;

/**
 * <p>Title: MatchMakerBoot</p>
 * 
 * <p>Description: The boot class for the MCS Match Maker application.  When users join, they are
 * wrapped in a Player object and registered in the GLO namespace under their UserID.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class MatchMakerBoot implements SimBoot<MatchMakerBoot>, SimUserListener {

	private static final long serialVersionUID = 1L;
	
	private GLOReference<Folder> folderRoot;
	
	/*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimBoot#boot
     */
	public void boot(GLOReference bootGLO, boolean firstBoot) {
		SimTask task = SimTask.getCurrent();
		if (firstBoot) {
			System.out.println("MatchMakerBoot: firstBoot");
			if (task.findGLO("UsernameMap") == null) {
				task.createGLO(new GLOMap<String, UserID>(), "UsernameMap");
			}
			
			if (task.findGLO("LobbyMap") == null) {
				task.createGLO(new GLOMap<SGSUUID, GLOReference<Lobby>>(), "LobbyMap");
			}
			
			if (task.findGLO("GameRoomMap") == null) {
				task.createGLO(new GLOMap<SGSUUID, GLOReference<GameRoom>>(), "GameRoomMap");
			}
			
			folderRoot = task.createGLO(createRootFolder(task));

		}
		task.addUserListener(bootGLO);
		initChannels(task);
		
	}
	
	private void initChannels(SimTask task) {
		GLOReference<GLOMap<SGSUUID, GLOReference>> lobbyRef = task.findGLO("LobbyMap");
		openChannels(task, lobbyRef);
		
		GLOReference<GLOMap<SGSUUID, GLOReference>> gameRoomRef = task.findGLO("GameRoomMap");
		openChannels(task, gameRoomRef);
	}
	
	private void openChannels(SimTask task, GLOReference<GLOMap<SGSUUID, GLOReference>> gloMapRef) {
		GLOMap<SGSUUID, GLOReference> gloMap = gloMapRef.get(task);
		Iterator iterator = gloMap.keySet().iterator();
		List<GLOReference> channelRooms = new LinkedList<GLOReference>();
		HashMap<SGSUUID, GLOReference> map = new HashMap<SGSUUID, GLOReference>();
		while (iterator.hasNext()) {
			GLOReference ref = gloMap.get(iterator.next());
			ChannelRoom curRoom = (ChannelRoom) ref.get(task);
			ChannelID cid = task.openChannel(curRoom.getChannelName());
			curRoom.setChannelID(cid);
			map.put(cid, ref);
		}
		gloMap.clear();
		Iterator<SGSUUID> mapIterator = map.keySet().iterator();
		while (mapIterator.hasNext()) {
			SGSUUID curKey = mapIterator.next();
			gloMap.put(curKey, map.get(curKey));
		}
	}
	
	/*
     * Called when a new user connects to the server.  A new Player object is
     * constructed and set as the user's "command proxy".  A new task is queued
     * to join the user to the Lobby Manager Control channel.
     * 
     * @see com.sun.gi.logic.SimUserListener#userJoined
     */
    public void userJoined(UserID uid, Subject subject) {
    	SimTask task = SimTask.getCurrent();
    	System.out.println("Match Maker User Joined");

    	GLOMap<String, UserID> userMap = (GLOMap<String, UserID>) task.findGLO("UsernameMap").get(task);
    	System.out.println("userJoined: map size " + userMap.size());
    	// TODO sten: don't know how to handle duplicate logins yet.
    	//if (!userMap.containsKey(uid)) {
    		Set<Principal> principles = subject.getPrincipals();
    		Principal principal = principles.iterator().next();
    		String username = principal.getName();
    		userMap.put(username, uid);
    		Player p = new Player(uid, username, folderRoot);
    		
    		System.out.println("Adding username " + username +  " uid " + uid);
    		
    		// map the player reference to its UserID for later lookup by other players.
    		GLOReference<Player> pRef = task.findGLO(uid.toString());
    		if (pRef == null) {
    			pRef = task.createGLO(p, uid.toString());
    			if (pRef == null) {
    				pRef = task.findGLO(uid.toString());
    			}
    		}
    		task.addUserDataListener(uid, pRef);
    		
    		task.join(uid, task.openChannel(CommandProtocol.LOBBY_MANAGER_CONTROL_CHANNEL));
    	//}
    }

	/*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserListener#userLeft
     */
	public void userLeft(UserID uid) {
		SimTask task = SimTask.getCurrent();
		GLOReference pRef = task.findGLO(uid.toString());
		
		if (pRef == null) {
			return;
		}
		Player player = (Player) pRef.get(task);
		GLOMap<String, UserID> userMap = (GLOMap<String, UserID>) task.findGLO("UsernameMap").get(task);
		if (userMap.containsKey(player.getUserName())) {
			System.out.println("removing " + player.getUserName() + " from map");
			userMap.remove(uid);
		}
		// this currently throws an exception
		//pRef.delete(task);
	}
	
	/**
	 * Creates the root of the Folder tree, creating Lobbies and subfolders along
	 * the way.  
	 * 
	 * 
	 * @param task		the SimTask to generate all the GLOReferences.
	 * 
	 */
	private Folder createRootFolder(SimTask task) {
		URL url = null;
		try {
			url = new URL("file:apps/matchmaker/matchmaker_config.xml");
			//url = new URL("file:release/apps/matchmaker/matchmaker_config.xml");
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		ConfigParser parser = new ConfigParser(url);
		
		return parser.getFolderRoot(task);
		
	}
	
	private Folder createTestFolders(SimTask task) {
		Folder root = new Folder("Test Game", "Some Description");
		
		Folder sub1 = new Folder("I can play!", "Some Description");
		Folder sub2 = new Folder("Hurt Me Plenty", "Some Description");
		Folder sub3 = new Folder("Nightmare", "Some Description");
		
		root.addFolder(task.createGLO(sub1));
		root.addFolder(task.createGLO(sub2));
		root.addFolder(task.createGLO(sub3));
		
		return root;
	}

}
