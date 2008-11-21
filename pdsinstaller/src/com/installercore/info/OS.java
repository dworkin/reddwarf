package com.installercore.info;

/**
 * Contains an {@code enum} for each possible type of supported {@code OS}.
 * @author Paul Gibler
 *
 */
public enum OS
{
	WINDOWS_XP		("Windows XP", "windows", Kernel.WINDOWS),
	WINDOWS_VISTA	("Windows Vista", "windows", Kernel.WINDOWS),
	WINDOWS_2000	("Windows 2000", "windows", Kernel.WINDOWS),
	WINDOWS_2003	("Windows 2003", "windows", Kernel.WINDOWS),
	MAC_OSX			("Mac OS X", "macosx", Kernel.UNIX),
	SOLARIS			("Solaris", "solaris", Kernel.UNIX),
	LINUX			("Linux", "linux", Kernel.UNIX);
	
	private String asString;
	private String asSimpleString;
	private Kernel kernel;
	
	OS(String asString, String asSimpleString, Kernel kernel)
	{
		this.asString = asString;
		this.asSimpleString = asSimpleString;
		this.kernel = kernel;
	}
	
	public String toString()
	{
		return this.asString;
	}
	
	public String toSimpleString()
	{
		return this.asSimpleString;
	}
	
	public boolean isWindows()
	{
		return kernel.equals(Kernel.WINDOWS);
	}
	
	public boolean isUnix()
	{
		return kernel.equals(Kernel.UNIX);
	}
	
	public static OS stringToOS(String osStr)
	{
		for(OS currOS : OS.values())
		{
			if(currOS.toString().equalsIgnoreCase(osStr))
			{
				return currOS;
			}
		}
		return null;
	}
}