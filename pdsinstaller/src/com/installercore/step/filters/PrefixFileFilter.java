package com.installercore.step.filters;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Filters only folders and only if their name starts with the specified prefix.
 * @author Paul Gibler
 */
public class PrefixFileFilter implements FilenameFilter {
	
	private String prefix;
	
	/**
	 * Constructor for PrefixFileFilter.
	 * @param prefix The prefix of the files to search for.
	 */
	public PrefixFileFilter(String prefix)
	{
		this.prefix = prefix;
	}
	
	/**
	 * Accepts only files whose name begin with the specified file prefix.
	 * @param dir The directory in which the file is located.
	 * @param name The name of the string.
	 */
	public boolean accept(File dir, String name) {
		return name.startsWith(prefix);
	}
}
