package com.sun.gi.apps.mcs.matchmaker.jme;

import com.sun.gi.utils.jme.SGSUUIDJMEImpl;



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
public class JMEFolderDescriptor {
	
	private SGSUUIDJMEImpl folderID;
	private String name;
	private String description;
	
	public JMEFolderDescriptor(SGSUUIDJMEImpl id, String name, String desc) {
		this.folderID = id;
		this.name = name;
		this.description = desc;
	}
	
	public SGSUUIDJMEImpl getFolderID() {
		return folderID;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}

}
