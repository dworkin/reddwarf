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
					if(!installPath.endsWith("/"))
					{
						installPath += "/";
					}
				}
				else
				{
					installPath = "." + File.separator + "projectdarkstar";
				}
				
				File pathOfInstall = new File(installPath);
				pathOfInstall.mkdirs();
				
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
