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

import java.util.regex.Pattern;

/**
 * {@link ValidateInstallationPathStep} is an {@link IStep} that validates the
 * install of an application
 * @author Paul Gibler.
 *
 */
public class ValidateInstallationPathStep implements IStep {
	
	private String installPath = "";
	
	/**
	 * Constructor for {@link ValidateInstallationPathStep}.
	 * This class validates that the install path that it knows about is a legal one.
	 * @param installPath The installation path of the application.
	 */
	public ValidateInstallationPathStep(String installPath)
	{
		this.installPath = installPath;
	}
	
	public void run() throws StepException {
		Pattern p = null;
		String unsupportedWindowsCharacters = ".*[<>:\\*\\\"\\?\\|].*";
		String os = System.getProperty("os.name");
		if(os.toLowerCase().startsWith("windows"))
		{
			String installPathOld = installPath;
			try
			{
				char fileSep = this.installPath.charAt(2);
				this.installPath = installPath.substring(installPath.indexOf(fileSep), installPath.length());
			} catch (StringIndexOutOfBoundsException e) {
				this.installPath = installPathOld;
			}
			p = Pattern.compile(unsupportedWindowsCharacters);
		}
		else
		{
			return;
		}
		
		if(p.matcher(installPath).matches())
		{
			throw new StepException("Invalid install path");
		}
	}
}
