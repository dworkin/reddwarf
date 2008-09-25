package com.installercore.step;

import java.io.File;
import java.util.Set;

import com.dsinstaller.DSInstallerStrings;
import com.installercore.metadata.*;

public class RunMetadataStep implements IStep {
	
	IStringReportCallback myCallback;
	Runnable endOfThread;
	Runnable endOfItem;
	String installPath;
	
	public RunMetadataStep(String installPath, IStringReportCallback callback, Runnable EndOfInstall, Runnable EndOfItem)
	{
		this.installPath = installPath;
		myCallback = callback;
		endOfThread = EndOfInstall;
		this.endOfItem = EndOfItem;
	}

	public void run() throws StepException {
		Set<String> metadata = MetadataDatabase.getMetadataNames();
		
		for(String mdataName : metadata)
		{
			IMetadata mdata = MetadataDatabase.getValue(mdataName);
			if(mdata instanceof EmbeddedMetadata)
			{
				IStep embeddedStep = null;
				if(fileExtension(mdata.getFileName()).equals("zip"))
				{
					embeddedStep = new EmbeddedUnzipStep(
							DSInstallerStrings.targetLocation +
							mdata.getSource() +
							mdata.getFileName(),
							installPath +
							mdata.getDestination(),
							myCallback);
				}
				else
				{
					embeddedStep = new EmbeddedMoveStep(
							mdata.getSource() +
							mdata.getFileName(),
							installPath +
							mdata.getDestination());
				}
				embeddedStep.run();
			}
			else if(mdata instanceof ExternalMetadata)
			{
				MoveFileByPrefixStep mfbps = new MoveFileByPrefixStep(
						mdata.getSource(),
						installPath +
						mdata.getDestination());
				mfbps.run();
			}
			javax.swing.SwingUtilities.invokeLater(endOfItem);
		}
		if(endOfThread != null)
		{
			javax.swing.SwingUtilities.invokeLater(endOfThread);
		}
	}
	
	private String fileExtension(String name)
	{
		if(name.contains(File.separator))
		{
			name = name.substring(name.lastIndexOf(File.separator)+1, name.length());
		}
		if(name.contains("."))
		{
			name = name.substring(name.lastIndexOf(".")+1, name.length());
		}
		return name;
	}

}
