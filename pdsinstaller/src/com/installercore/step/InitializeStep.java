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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.installercore.metadata.MetadataDatabase;
import com.installercore.var.VarDatabase;

public class InitializeStep implements IStep {
	
	private String metadata;
	private String sysvars;
	
	public InitializeStep(String metadataLocation, String sysvarsLocation)
	{
		this.metadata = metadataLocation;
		this.sysvars = sysvarsLocation;
	}

	public void run() throws StepException {
		URL url;
		InputStream stream;
		try
		{
			url = this.getClass().getResource(sysvars);
			stream = url.openStream();
			VarDatabase.populate(stream);
		} catch (IOException e) {
			throw new StepException("Unable to access metadata resource file. This isn't your fault.");
		}
		try {
			url = this.getClass().getResource(metadata);
			stream = url.openStream();
			MetadataDatabase.populate(stream);
		} catch (IOException e) {
			throw new StepException("Unable to access metadata resource file. This isn't your fault.");
		}		
	}
}
