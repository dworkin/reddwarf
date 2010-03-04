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

package com.dsinstaller;

import java.io.File;

import com.installercore.ApplicationArguments;
import com.installercore.CommandLineInstaller;
import com.installercore.InstallerManager;
import com.installercore.IIManagerCallback;
import com.dsinstaller.view.*;

/**
 * {@link Installer} is the starting point at which the program runs.
 * @author Chris Scalabrini
 * @author Paul Gibler
 *
 */
public class Installer {
	/**
	 * The launching point of the application. The program determines if
	 * it is supposed to run as a command-line, one-command installer or
	 * if it should instead spawn a graphical interface for the installer.
	 * In both cases, the installer works in a similar manner.
	 * @param args The command line arguments passed into the Installer.
	 */
	public static void main(String[] args) {
		ApplicationArguments arguments = null;
		String installPath = "";
		try {
			arguments = ApplicationArguments.parseCommandLine(args);
		} catch (Exception e) {
			System.err.println("Could not properly parse command line arguments.");
			System.exit(1);
		}
		
		if(arguments.hasArgument("help"))
		{
			System.out.println("Usage: pdsinstaller.jar [options]");
			System.out.println("Options:");
			System.out.println("\t-nogui : Run the installer without a GUI.");
			System.out.println("\t-path : Set the path of the installation.");
			System.out.println("\t-help : Display help text.");
		}
		
		if(arguments.hasArgument("nogui"))
		{
			try {
				
				if(arguments.hasArgument("path"))
				{
					installPath = arguments.getArgumentValue("path");
				}
				else
				{
					installPath = "." + File.separator + "projectdarkstar";
				}
				
				File pathOfInstall = new File(installPath);
				pathOfInstall.mkdirs();
				
				if(!installPath.endsWith("/"))
				{
					installPath += "/";
				}
				
				new CommandLineInstaller(installPath).install();
			} catch (Exception e) {
				System.err.println("Could not properly install Project Darkstar.");
				e.printStackTrace();
			}
		}
		else
		{
			new InstallerManager(
				new IIManagerCallback()
				{
					public void CallEvent(Object arguments) {
						System.exit(0);
					}	
				},
				new WelcomePanel());
		}
	}
}
