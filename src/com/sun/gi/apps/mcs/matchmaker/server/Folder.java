package com.sun.gi.apps.mcs.matchmaker.server;

import java.util.ArrayList;
import java.util.List;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

/**
 * 
 * <p>Title: Folder</p>
 * 
 * <p>Description: Represents a Lobby Folder in the Match Maker application.  A Folder can contain 
 * any number of subfolders and Lobbies.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class Folder implements GLO {
	
	private List<GLOReference> folderList;
	private List<GLOReference> lobbyList;
	
	private String name;
	private String description;
	private SGSUUID folderID;
	
	public Folder(String name, String description) {
		this.name = name;
		this.description = description;
		folderList = new ArrayList<GLOReference>();
		lobbyList = new ArrayList<GLOReference>();
		folderID = new StatisticalUUID();
	}
	
	/**
	 * Adds a subfolder to the Folder list.
	 * 
	 * @param folder			the folder to add. 
	 */
	public void addFolder(GLOReference folder) {
		folderList.add(folder);
	}
	
	/**
	 * Adds a Lobby to the lobby list as a GLOReference.
	 * 
	 * @param lobby			the lobby to add.
	 */
	public void addLobby(GLOReference lobby) {
		lobbyList.add(lobby);
	}
	
	/**
	 * Called recursively in an attempt to find the folder in the hierarchy 
	 * matching the given folderID.  Returns null if no matching folder is found.
	 * 
	 * @param task				the SimTask used to peek at the folders.
	 * @param folderID			the UUID to match on.
	 * 
	 * @return the Folder with the matching folderID, or null if not found. 
	 */
	public Folder findFolder(SimTask task, SGSUUID folderID) {
		for (GLOReference folderRef : folderList) {
			Folder curFolder = (Folder) folderRef.peek(task);
			if (curFolder.getFolderID().equals(folderID)) {
				return curFolder;
			}
			Folder subFolder = curFolder.findFolder(task, folderID);
			if (subFolder != null) {
				return subFolder;
			}
		}
		
		return null;
	}
	
	/**
	 * Attempts to find a Lobby in the lobby list with a lobby name matching lobbyName. 
	 * "Peek" access is used to do the lookups.  If a matching Lobby is found, "get" access 
	 * is used.
	 * 
	 * @param task					the SimTask
	 * @param lobbyName				the name to lookup
	 * 
	 * @return any matching Lobby with "get" access.
	 */
	public Lobby findLobby(SimTask task, String lobbyName) {
		for (GLOReference ref : lobbyList) {
			Lobby curLobby = (Lobby) ref.peek(task);
			if (curLobby.getName().equals(lobbyName)) {
				return (Lobby) ref.get(task);
			}
		}
		return null;
	}
	
	/**
	 * Returns a list of GLOReferences of type Lobby that this
	 * Folder is hosting.
	 * 
	 * @return	a list of GLOReferences of Lobbies.
	 */
	public List<GLOReference> getLobbies() {
		return lobbyList;
	}
	
	/**
	 * Returns a list of GLOReferences of type Folder which 
	 * represents the subfolders.
	 * 
	 * @return alist of GLOReferences of Folders.
	 */
	public List<GLOReference> getFolders() {
		return folderList;
	}
	
	/**
	 * Returns this Folder's name.
	 * 
	 * @return	the folder name.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Returns this Folder's description.
	 * 
	 * @return the description of this folder.
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Returns this Folder's UUID.
	 * 
	 * @return the Folder's UUID.
	 */
	public SGSUUID getFolderID() {
		return folderID;
	}

}
