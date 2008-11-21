package com.installercore.step;

import java.io.File;

import com.installercore.step.filters.PrefixFileFilter;

/**
 * Searches for a File by prefix and then allows the user
 * of the class to retrieve a File object with information
 * about the found file.
 * @author Paul Gibler
 *
 */
public class FindFileByPrefixStep implements IStep {
	
	private File folder = null;
	private File file = null;
	private File top = null;
	private String folderPrefix = null;
	private String filePrefix = null;
	
	/**
	 * FileFileByPrefix searches in a path for a folder with a certain prefix for a file
	 * with a certain prefix. This component is used to deal with the problem of software
	 * versioning in instances where folders and files will be prepended with a version number
	 * when all that needs to be done is a search for a file regardless of version. This will
	 * search for that file and return a File handle pointing towards the found file.
	 * @param top The top level File folder in which the folder and file can be found.
	 * @param folderPrefix The prefix of the folder that contains the file which this component searches for.
	 * @param filePrefix The prefix of the file to be searched for.
	 * @see File
	 */
	public FindFileByPrefixStep(File top, String folderPrefix, String filePrefix)
	{
		this.top = top;
		this.folderPrefix = folderPrefix;
		this.filePrefix = filePrefix;
	}
	
	/**
	 * To find the desired file, a search is dispatched first to find the folder
	 * in which the file resides. This is done by looking at all files within the top level
	 * folder and taking the first one whose prefix matches the {@link folderPrefix}
	 */
	public void run() throws StepException {
		try
		{
			File folder = top.listFiles(new PrefixFileFilter(this.folderPrefix))[0];
			file = folder.listFiles(new PrefixFileFilter(this.filePrefix))[0];
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new StepException("File not found: "+this.folder);
		} catch (NullPointerException e) {
			throw new StepException("Folder not found: "+this.folder);
		}
	}

	/**
	 * Returns a File handler to the 
	 * @return The found file.
	 */
	public File getFile()
	{
		return this.file;
	}
}
