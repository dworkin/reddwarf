package com.installercore.metadata;

/**
 * {@link AbstractMetadata} is a data structure that contains all data the common
 * behavior that all classes that implement {@link IMetadata} should contain.
 * This class may be subclassed to avoid writing excess code for classes that
 * wish to implement IMetadata.
 * @author Paul Gibler
 *
 */
public abstract class AbstractMetadata implements IMetadata {
	protected String destination;
	protected String name;
	protected String source;
	protected String fileName;
	
	/**
	 * Constructor for {@link AbstractMetadata}. Sets all of the common data structures.
	 * within the AbstractMetadata to their proper value.
	 * @param name The unique identifier of the metadata.
	 * @param source The folder or resource path containing the data.
	 * @param fileName The name of the data file or folder.
	 * @param destination The destination folder of the data.
	 */
	public AbstractMetadata(String name, String source, String fileName, String destination)
	{
		this.name = name;
		this.source = source;
		this.fileName = fileName;
		this.destination = destination;
	}

	/**
	 * Returns the name of the data.
	 * @return the name of the data.
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * Returns the destination folder of the data.
	 * @return the destination folder of the data.
	 */
	public String getDestination() {
		return this.destination;
	}

	/**
	 * Returns the source folder or resource path of the data.
	 * @return the source folder or resource path of the data.
	 */
	public String getSource() {
		return this.source;
	}
	
	/**
	 * Returns the file or folder name of the data.
	 * @return the file or folder name of the data.
	 */
	public String getFileName() {
		return this.fileName;
	}

}
