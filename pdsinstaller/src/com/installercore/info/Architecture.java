package com.installercore.info;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains an {@code enum} type for each possible {@code Architecture} type. 
 * @author Paul Gibler
 *
 */
public enum Architecture
{
	x86 ("x86", "i386", "i486", "i586", "i686"),
	x64 ("x64", "x86_64", "amd64"),
	SPARC ("sparc"),
	POWER_PC ("ppc");
	
	private Set<String> possibilities;
	private String identity;
	
	Architecture(String identity, String... asString)
	{
		this.identity = identity;
		this.possibilities = new HashSet<String>();
		this.possibilities.add(identity);
		for(String s : asString)
		{
			this.possibilities.add(s);
		}
	}
	
	public static Architecture stringToArch(String archStr)
	{
		for(Architecture arch : Architecture.values())
		{
			for(String s : arch.types())
			{
				if(s.equals(archStr))
				{
					return arch;
				}
			}
		}
		return null;
	}
	
	/**
	 * Returns a {@code Set} of all the possible types.
	 * @return A {@code Set} of the unique architecture types.
	 */
	private Set<String> types()
	{
		return this.possibilities;
	}
	
	public String toString()
	{
		return this.identity;
	}
}