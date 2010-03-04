/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

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
