package com.installercore.metadata;

/**
 * {@link ExternalFileMetadata} is a data structure that stores the source of a file,
 * the destination of a file, and a global file handle for the file.  
 * @author Paul Gibler
 *
 */
public class ExternalMetadata extends AbstractMetadata {

	/**
	 * Stores a metadata file or folders globally accessible name, the folder in which
	 * the metadata file or folder is contained, the filename of the file or folder, and
	 * its destination, which must include the name for the file or folder.
	 * @param name The globally accessible name of the metadata.
	 * @param source The folder that contains the metadata file or folder.
	 * @param file The name of the file or folder.
	 * @param destination The path including filename of the file or folder after it is moved.
	 */
	public ExternalMetadata(String name, String source, String filePath, String destination)
	{
		super(name, source, filePath, destination);
	}
}
