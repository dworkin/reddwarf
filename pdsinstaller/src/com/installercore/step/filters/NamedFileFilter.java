package com.installercore.step.filters;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Filters out the folder with the specified name.
 * @author Paul Gibler
 */
public class NamedFileFilter implements FilenameFilter {
	
	private String searchName;
	
	public NamedFileFilter(String searchName)
	{
		this.searchName = searchName;
	}
	
	/**
	 * Accepts only directories whose name contains 'bdb'.
	 * 
	 * @param dir The directory in which the file is located.
	 * @param name The name of the string.
	 */
	public boolean accept(File dir, String name) {
		return name.equals(searchName);
	}
}
