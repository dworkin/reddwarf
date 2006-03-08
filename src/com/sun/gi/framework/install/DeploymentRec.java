package com.sun.gi.framework.install;

import java.util.List;

public interface DeploymentRec {

	/**
	 * listUserManagers
	 *
	 * @return Iterator
	 */
	public  List<UserMgrRec> getUserManagers();

	/**
	 * userManagerCount
	 *
	 * @return String
	 */
	public int userManagerCount();

	

	/**
	 * getName
	 *
	 * @return String
	 */
	public String getName();
	
	/**
	 * getClasspathURL
	 *
	 * @return String
	 */
	public String getClasspathURL();
	
	/**
	 * getBootClass
	 *
	 * @return String
	 */
	public String getBootClass();

	/**
	 * getDescription
	 *
	 * @return String
	 */
	public String getDescription();

	public int getID();
	
	/**
	 * This is set after the creation the rec itself so must be visible in the interface
	 * @param ID id assigned to this application whe it was installed into SGS
	 * 
	 */
	public void setID(int ID);

}