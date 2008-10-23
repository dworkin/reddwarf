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
