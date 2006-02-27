package com.sun.gi.apps.mcs.matchmaker.server;

import java.io.IOException;
import java.net.URL;

import javax.security.auth.Subject;

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
 * <p>Description: The boot class for the MCS Match Maker application.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class MatchMakerBoot implements SimBoot, SimUserListener {

	private GLOReference folderRoot;
	private GLOMap<SGSUUID, GLOReference> lobbyMap;
	private GLOMap<SGSUUID, GLOReference> gameRoomMap;
	
	/*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimBoot#boot
     */
	public void boot(GLOReference bootGLO, boolean firstBoot) {
		SimTask task = SimTask.getCurrent();
		if (firstBoot) {
			System.out.println("MatchMakerBoot: firstBoot");
			GLOMap<String, UserID> userMap = new GLOMap<String, UserID>();
			task.createGLO(userMap, "UsernameMap");
			
			lobbyMap = new GLOMap<SGSUUID, GLOReference>();
			task.createGLO(lobbyMap, "LobbyMap");
			
			gameRoomMap = new GLOMap<SGSUUID, GLOReference>();
			task.createGLO(gameRoomMap, "GameRoomMap");
			
			folderRoot = task.createGLO(createRootFolder(task));

		}
		task.addUserListener(bootGLO);
		
	}
	
	/*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserListener#userJoined
     */
    public void userJoined(UserID uid, Subject subject) {
    	SimTask task = SimTask.getCurrent();
    	System.out.println("Match Maker User Joined");

    	GLOMap<String, UserID> userMap = (GLOMap<String, UserID>) task.findGLO("UsernameMap").get(task);
    	System.out.println("userJoined: map size " + userMap.size());
    	//if (!userMap.containsKey(uid)) {
    		String username = null;
    		for (Object curCredential : subject.getPublicCredentials()) {
    			username = (String) curCredential;
    		}
    		userMap.put(username, uid);
    		Player p = new Player(uid, username, folderRoot);
    		
    		System.out.println("Adding username " + username +  " uid " + uid);
    		
    		// map the player reference to its UserID for later lookup by other players.
    		GLOReference pRef = task.createGLO(p, uid.toString());
    		task.addUserDataListener(uid, pRef);
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
		Player player = (Player) pRef.get(task);
		GLOMap<String, UserID> userMap = (GLOMap<String, UserID>) task.findGLO("UsernameMap").get(task);
		if (userMap.containsKey(player.getUserName())) {
			userMap.remove(uid);
		}
		pRef.delete(task);
	}
	
	/**
	 * Creates the root of the Folder tree, creating Lobbies and subfolders along
	 * the way.  
	 * 
	 * 
	 *   TODO sten: currently this just genereates test data until the real 
	 *   scheme is know.
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
