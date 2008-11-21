package com.installercore.info;

import java.io.File;

/**
 * This class is used to figure out what platform the application is running on
 * and set the platform and path separator variables appropriately.
 * @author Paul Gibler
 */
public class SystemInfo {	
	// Class variables.
	
	private static OS os = OS.stringToOS(System.getProperty("os.name"));
	private static Architecture architecture = Architecture.stringToArch(
													System.getProperty("os.arch"));
	private static String pathSeperator = File.separator;
	private static String nameSeparator = File.pathSeparator;
	
	private SystemInfo()
	{		
	}

	/**
	 * Gets the current {@code OS} that the application is running on.
	 * @return The current {@code OS} that the application is running on.
	 */
	public static OS getOS()
	{
		return os;
	}
	
	/**
	 * Gets the current {@code Architecture} that the application is running on.
	 * @return The current {@code Architecture} that the application is running on.
	 */
	public static Architecture getArchitecture()
	{
		return architecture;
	}
	
	/**
	 * Gets the file separator of the operating system that the application is running on.
	 * @return The file separator of the operating system that the application is running on.
	 */
	public static String getFileSeparator()
	{
		return pathSeperator;
	}
	
	/**
	 * Gets the path separator of the operating system that the application is running on.
	 * @return The path separator of the operating system that the application is running on.
	 */
	public static String getPathSeparator()
	{
		return nameSeparator;
	}
}
