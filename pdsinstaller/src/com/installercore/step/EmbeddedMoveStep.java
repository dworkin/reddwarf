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

import com.dsinstaller.DSInstallerStrings;

public class EmbeddedMoveStep implements IStep {

	String source = null;
	String destination = null;
	
	/**
	 * 
	 * @param source  The relative path (relative to THIS class) of the embedded resource.
	 * @param destination The absolute path to the location on the disk where it's going.
	 */
	public EmbeddedMoveStep(String source, String destination)
	{
		this.source = DSInstallerStrings.targetLocation + source;
		this.destination = destination;
	}
	
	/**
	 * If there are any errors, it will throw a StepException.
	 */
	public void run() throws StepException {
		try
		{
			final int bufferSize = 2048;
			URL url = this.getClass().getResource(source);
			InputStream stream = url.openStream();
			
			File f = new File(destination);
			String path = f.getAbsolutePath();
			path = path.substring(0,path.lastIndexOf(File.separator));
			f = new File(path);
			f.mkdirs();
			
			byte data[] = new byte[bufferSize];
			int count = 0;
			
			FileOutputStream ostream = new FileOutputStream(destination);
			BufferedOutputStream output  = new BufferedOutputStream(ostream, bufferSize);
			while((count = stream.read(data, 0, bufferSize)) != -1)
			{
				output.write(data,0,count);
			}
			output.flush();
			output.close();
			stream.close();
		}
		catch (Exception e)
		{
			throw new StepException();
		}	
	}

}
