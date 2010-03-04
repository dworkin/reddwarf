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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.*;

/**
 * {@code EmbeddedUnzipStep} takes a zip file contained in the jar as a resource and 
 * unzips it to a destination in the file system.
 *
 */
public class EmbeddedUnzipStep implements IStep {

	String resourceLocation = null;
	String resourceDestination = null;
	String innerFile = null;
	IStringReportCallback myCallback = null;
	
	/**
	 * Creates an {@code EmbeddedUnzipStep} capable of unzipping a zip file contained
	 * as a resource and unzipping it into the file system.
	 * @param resourceLocation The relative path (relative to THIS class) of the embedded resource.
	 * @param resourceDestination The absolute path to the location on the disk where it's going.
	 * @param callback How the program updates the UI.
	 */
	public EmbeddedUnzipStep(String resourceLocation, String resourceDestination, IStringReportCallback callback)
	{
		initialize(resourceLocation, resourceDestination, callback);
	}
	
	/**
	 * Creates an {@code EmbeddedUnzipStep} capable pulling a file or directory out
	 * of the zip file and unzipping it to a directory in the file system.
	 * @param resourceLocation The relative path (relative to THIS class) of the embedded resource.
	 * @param resourceDestination The absolute path to the location on the disk where it's going.
	 * @param callback How the program updates the UI.
	 * @param innerFile The file or directory within the zip directory structure.
	 */
	public EmbeddedUnzipStep(String resourceLocation, String resourceDestination, IStringReportCallback callback, String innerFile)
	{
		initialize(resourceLocation, resourceDestination, callback);
		this.innerFile = innerFile;
		if(innerFile.startsWith("/"))
		{
			innerFile = innerFile.substring(1, innerFile.length());
		}
	}
	
	private void initialize(String resourceLocation, String resourceDestination, IStringReportCallback callback)
	{
		this.resourceLocation = resourceLocation;
		this.resourceDestination = resourceDestination;
		this.myCallback = callback;
	}
	
	
	/**
	 * Takes the zip file contained as a resource in the jar and unzips it to the
	 * destination in the file system as set in the constructor.
	 * @throws StepException If the unzip does not succeed.
	 */
	public void run() throws StepException {
		try
		{
			BufferedOutputStream destination = null;
			final int bufferSize = 2048;
			URL url = this.getClass().getResource(resourceLocation);
			InputStream stream = url.openStream();
			ZipInputStream zstream = new ZipInputStream(stream);
			ZipEntry entry;
			if(innerFile != null)
			{
				boolean foundFile = false;
				String removeChunk = null;
				if(innerFile.lastIndexOf("/") != -1)
					removeChunk = innerFile.substring(0,innerFile.lastIndexOf("/"));
				while((entry = zstream.getNextEntry()) != null)
				{
					if(atInnerFile(innerFile, entry))
					{
						if(isDirectory(innerFile, entry))
						{
							removeChunk = innerFile;
							if(!removeChunk.endsWith("/"))
							{
								removeChunk += "/";
							}
						}
						unzipEntry(entry, zstream, destination, bufferSize, removeChunk);
						if(!foundFile)
							foundFile = true;
					}
				}
				if(!foundFile)
					throw new Exception("Could not find internal file in zip file");
				return;
			}
			else
			{
				while((entry = zstream.getNextEntry()) != null)
				{
					unzipEntry(entry, zstream, destination, bufferSize);
				}
				stream.close();
				if(myCallback != null)
					myCallback.report(null);
			}
		}
		catch (Exception e)
		{
			if(myCallback != null)
				myCallback.report("Extraction error.");
			throw new StepException("Error occured during unzip");
		}
	}
	
	private boolean atInnerFile(String file, ZipEntry currEntry)
	{
		return currEntry.toString().startsWith(file);
	}

	private boolean isDirectory(String file, ZipEntry currEntry)
	{
		String path = currEntry.toString();
		return path.substring(file.length()).startsWith("/");
	}
	
	private void unzipEntry(ZipEntry entry, ZipInputStream zstream, BufferedOutputStream destination, final int bufferSize) throws Exception
	{
		unzipEntry(entry, zstream, destination, bufferSize, null);
	}
	
	private void unzipEntry(ZipEntry entry, ZipInputStream zstream, BufferedOutputStream destination, final int bufferSize, String removeFromBeginning) throws Exception
	{
		if(myCallback != null)
			myCallback.report(entry.getName());
		
		int count = 0;
		byte data[] = new byte[bufferSize];
		String outName = entry.getName();
		if(removeFromBeginning != null)
		{
			if(outName.startsWith(removeFromBeginning))
			{
				outName = outName.substring(removeFromBeginning.length());
			}
		}
		File f = new File(resourceDestination + outName);
		String path = f.getAbsolutePath();
		
		if(!entry.isDirectory())
		{
			path = path.substring(0,path.lastIndexOf(File.separator));
		}
		
		f = new File(path);
		f.mkdirs();
		
		if(!entry.isDirectory())
		{
			FileOutputStream ostream = new FileOutputStream(resourceDestination + outName);
			destination = new BufferedOutputStream(ostream, bufferSize);
			while((count = zstream.read(data, 0, bufferSize)) != -1)
			{
				destination.write(data,0,count);
			}
			destination.flush();
			destination.close();
		}
	}
}
