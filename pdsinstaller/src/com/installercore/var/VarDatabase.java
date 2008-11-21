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
