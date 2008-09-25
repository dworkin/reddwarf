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
			new InitializeStep(DSInstallerStrings.metadataLocation).run();
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
