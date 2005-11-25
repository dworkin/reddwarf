package com.sun.gi.framework.install.impl;

import java.util.*;

import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.UserMgrRec;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

public class DeploymentRecImpl implements DeploymentRec {
	int id;

	String name;
	String classpathURL=null;

	String bootClass = null;

	Map bootClassParameters = null;

	List<UserMgrRec> userManagers = new ArrayList<UserMgrRec>();

	/**
	 * InstallRec
	 * @param gameURL 
	 * 
	 * @param i
	 *            int
	 * @param iNSTALLATION
	 *            INSTALLATION
	 */
	public DeploymentRecImpl(String gameName) {
		name = gameName;
		
	}
	
	public void setGLEapp(String bootClassFQDN, String classpathURL){
		bootClass = bootClassFQDN;
		this.classpathURL = classpathURL;
	}

	/**
	 * makeParameterMap
	 * 
	 * @param paramList
	 *            List
	 * @return Map
	 */
	public static Map makeParameterMap(List paramList) {
		return null;
	}

	public void addUserManager(UserMgrRec rec) {
		userManagers.add(rec);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.install.DeploymentRec#listUserManagers()
	 */
	public List<UserMgrRec> getUserManagers() {
		return userManagers;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.install.DeploymentRec#userManagerCount()
	 */
	public int userManagerCount() {
		return userManagers.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.install.DeploymentRec#getName()
	 */
	public String getName() {
		return name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.install.DeploymentRec#getDescription()
	 */
	public String getDescription() {
		return "";
	}

	public void setID(int id) {
		this.id = id;
	}

	public int getID() {

		return id;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.framework.install.DeploymentRec#getClasspathURL()
	 */
	public String getClasspathURL() {
		return classpathURL;
	}
	
	public String getBootClass(){
		return bootClass;
	}

}
