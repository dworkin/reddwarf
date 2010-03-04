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

package com.installercore.info;

import java.io.File;

/**
 * This class is used to figure out what platform the application is running on
 * and set the platform and path separator variables appropriately.
 * @author Paul Gibler
 */
public class SystemInfo {	
	// Class variables.
	
	private static OS os = OS.stringToOS(System.getProperty("os.name"));
	private static Architecture architecture = Architecture.stringToArch(
													System.getProperty("os.arch"));
	private static String pathSeperator = File.separator;
	private static String nameSeparator = File.pathSeparator;
	
	private SystemInfo()
	{		
	}

	/**
	 * Gets the current {@code OS} that the application is running on.
	 * @return The current {@code OS} that the application is running on.
	 */
	public static OS getOS()
	{
		return os;
	}
	
	/**
	 * Gets the current {@code Architecture} that the application is running on.
	 * @return The current {@code Architecture} that the application is running on.
	 */
	public static Architecture getArchitecture()
	{
		return architecture;
	}
	
	/**
	 * Gets the file separator of the operating system that the application is running on.
	 * @return The file separator of the operating system that the application is running on.
	 */
	public static String getFileSeparator()
	{
		return pathSeperator;
	}
	
	/**
	 * Gets the path separator of the operating system that the application is running on.
	 * @return The path separator of the operating system that the application is running on.
	 */
	public static String getPathSeparator()
	{
		return nameSeparator;
	}
}
