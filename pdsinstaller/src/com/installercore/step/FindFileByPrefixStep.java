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
