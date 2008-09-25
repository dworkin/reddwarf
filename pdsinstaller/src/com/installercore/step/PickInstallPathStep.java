package com.installercore.step;

import java.io.File;
import java.io.IOException;

public class PickInstallPathStep implements IStep {
	
	private File itJustWorks = null;
	
	public void run() throws StepException {
		itJustWorks = new File("");
	}
	
	public String getInstallPath()
	{
		try
		{
			return itJustWorks.getCanonicalPath();
		} catch (IOException e) {
			return this.itJustWorks.getAbsolutePath();
		}
	}
}
