package com.sun.gi.apps.mcs.matchmaker.client;

import com.sun.gi.utils.SGSUUID;

/**
 * 
 * <p>Title: FolderDescriptor</p>
 * 
 * <p>Description: A simple immutable value object that represents the contents of a Folder
 * in the match making application.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class FolderDescriptor {
	
	private SGSUUID folderID;
	private String name;
	private String description;
	
	public FolderDescriptor(SGSUUID id, String name, String desc) {
		this.folderID = id;
		this.name = name;
		this.description = desc;
	}
	
	public SGSUUID getFolderID() {
		return folderID;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}

}
