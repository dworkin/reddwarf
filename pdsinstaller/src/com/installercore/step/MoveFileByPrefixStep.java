package com.installercore.step;

import java.io.File;

/**
 * Moves a file to a specified destination.
 * @author Paul Gibler
 * 
 */
public class MoveFileByPrefixStep implements IStep {
	
	private File origin;
	private File destination;
	
	/**
	 * MoveFileByPrefixStep moves a file to its destination.
	 * @param origin The original file to be moved.
	 * @param destination The destination of the file.
	 * @see File
	 */
	public MoveFileByPrefixStep(String origin, String destination)
	{
		this.origin = new File(origin);
		this.destination = new File(destination);
	}
	
	/**
	 * To find the desired file, a search is dispatched first to find the folder
	 * in which the file resides. This is done by looking at all files within the top level
	 * folder and taking the first one whose prefix matches the {@link folderPrefix}
	 */
	public void run() throws StepException {
		origin.renameTo(destination);
	}
}
