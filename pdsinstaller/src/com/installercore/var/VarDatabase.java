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

package com.installercore.var;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.installercore.info.SystemInfo;

/**
 * {@code VarDatabase} stores {@code Map} whose keys are system variables, and whose values are set
 * to the proper value depending on the system the application is running on.
 * @author Paul Gibler
 *
 */
public class VarDatabase {
	
	/**
	 * Contains all of the global variables.
	 */
	private static Map<String, String> vars = new HashMap<String, String>();
	
	/**
	 * Obtains the variable value that is associated with the input key value.
	 * @param member The name of the variable to be retrieved.
	 * @return The associated variable value if it is set, otherwise this returns null.
	 */
	public static String getValue(String member)
	{
		return vars.get(member);
	}
	
	/**
	 * Returns a {@link Set} of all of the system variable keys.
	 * @return A {@link Set} containing the names of each system variable key.
	 */
	public static Set<String> getSysVarNames()
	{
		return vars.keySet();
	}
	
	/**
	 * Returns the number of system variables stored in the database.
	 * @return The number of system variables stored in the database.
	 */
	public static int count()
	{
		return vars.size();
	}
	
	/**
	 * Populates the {@code VarDatabase} with the proper variables.
	 * @param is The {@code InputStream} to the properties file from which the variables will be extracted.
	 * @throws IOException If the {@code VarDatabase} if the properties cannot be loaded from the {@code InputStream}.
	 */
	public static void populate(InputStream is) throws IOException
	{
		Properties properties = new Properties();
		properties.load(is);
		String os = SystemInfo.getOS().toSimpleString();
		String arch = SystemInfo.getArchitecture().toString();
		String sys = os + "." + arch;
		Pattern p = Pattern.compile("^(.*?)\\.sys\\.(.*?)\\.(.*)$");
		Matcher m;
		
		for(Object o : properties.keySet())
		{
			String s = (String)o;
			m = p.matcher(s);
			if(m.matches())
			{
				String varName = m.group(1);
				String matchOS = m.group(2);
				String matchArch = m.group(3);
				
				if(matchOS.equals(os) && matchArch.equals(arch))
				{
					String fullVar = varName + ".sys." + sys;
					vars.put(varName, properties.getProperty(fullVar));
				}
			}
			else
			{
				vars.put(s, properties.getProperty(s));
			}
		}
		return;
	}
}
