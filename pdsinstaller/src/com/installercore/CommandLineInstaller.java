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

package com.installercore;

import java.io.File;
import java.io.IOException;

import com.dsinstaller.DSInstallerStrings;
import com.installercore.metadata.MetadataDatabase;
import com.installercore.step.*;

public class CommandLineInstaller {
	
	int metadataItems;
	int itemsInstalled = 0;
	boolean percentDisplayed = false;
	int percentDone = 0;
	String installPath;
	File installPathAsFile;
	
	public CommandLineInstaller(String installPath)
	{
		this.installPath = installPath;
		this.installPathAsFile = new File(installPath);
	}
	
	public void install() throws Exception
	{
		try {
			new InitializeStep(DSInstallerStrings.metadataLocation, DSInstallerStrings.sysvarsLocation).run();
			metadataItems = MetadataDatabase.count();
			new ValidateInstallationPathStep(installPath).run();
			
			RunMetadataStep step = new RunMetadataStep(	
					installPath,
					new StandardOutStringReportCallback(),
					new Runnable()
					{
						public void run() {
							System.out.println("\nInstallation completed successfully");
							try
							{
								System.out.println("Your Project Darkstar installation is located at "+installPathAsFile.getCanonicalPath());
							} catch (IOException e) {
								System.out.println("Your Project Darkstar installation is located at "+installPathAsFile.getAbsolutePath());
							}
						}
					},
					new Runnable()
					{
						public void run() {
							itemsInstalled += 1;
							if(percentDisplayed)
							{
								if(percentDone < 10)
								{
									System.out.print("\b\b");
								}
								else
								{
									System.out.print("\b\b\b");
								}
							}
							percentDone = Math.round(100*(float)itemsInstalled/(float)metadataItems);
							System.out.print(percentDone + "%");
							percentDisplayed = true;
						}
					}
				);
				step.run();
			
		} catch (StepException e) {
			throw new Exception();
		}
	}
}
