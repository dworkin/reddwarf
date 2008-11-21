package com.installercore.metadata;

import java.io.File;

/**
 * Represents a piece of metadata about a file or folder, namely the origin of
 * the file or folder and its intended destination.
 * @author Paul Gibler
 *
 */
public interface IMetadata {
	/**
	 * Returns the filename of the metadata file.
	 * @return The globally scoped name of the file.
	 */
	public String getName();
	/**
	 * Returns a {@link String} that points to the folder containing the metadata file.
	 * @return The origin of the metadata file as a {@link File} handle.
	 */
	public String getSource();
	/**
	 * Returns a {@link String} that contains the name of the file or folder indicated
	 * by the metadata.
	 * @return The name of the file or folder indicated by the metadata.
	 */
	public String getFileName();
	/**
	 * Returns a {@link String} that points to the intended destination of the
	 * metadata file.
	 * @return The intended destination of the metadata file as {@link File} handle.
	 */
	public String getDestination();
}
