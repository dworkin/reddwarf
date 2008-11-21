package com.installercore.step;

import java.util.Set;

import com.dsinstaller.DSInstallerStrings;
import com.installercore.metadata.*;

public class RunMetadataStep implements IStep {
	
	IStringReportCallback myCallback;
	Runnable endOfThread;
	Runnable endOfItem;
	String installPath;
	
	private static final String ZIP_EXT = ".zip";
	
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
			String source = mdata.getSource() + mdata.getFileName();
			String installLocation = installPath + mdata.getDestination();
			if(mdata instanceof EmbeddedMetadata)
			{
				IStep embeddedStep = null;
				if(isZip(source))
				{
					String internalPath = getInternalPath(source);
					if(internalPath == null)
					{
						embeddedStep = new EmbeddedUnzipStep(
								DSInstallerStrings.targetLocation + source,	installLocation,
								myCallback);
					}
					else
					{
						String zipPath = getExternalPath(source);
						embeddedStep = new EmbeddedUnzipStep(
							DSInstallerStrings.targetLocation + zipPath, installLocation,
							myCallback,
							internalPath);
					}
				}
				else
				{
					embeddedStep = new EmbeddedMoveStep(source, installLocation);
				}
				embeddedStep.run();
			}
			else if(mdata instanceof ExternalMetadata)
			{
				MoveFileByPrefixStep mfbps = new MoveFileByPrefixStep(source, installLocation);
				mfbps.run();
			}
			javax.swing.SwingUtilities.invokeLater(endOfItem);
		}
		if(endOfThread != null)
		{
			javax.swing.SwingUtilities.invokeLater(endOfThread);
		}
	}
	
	/**
	 * Returns {@code true} if the file is a zip file, otherwise {@code false}.
	 * @param path The path to the file.
	 * @return {@code true} if the file is a zip file, otherwise {@code false}.
	 */
	private boolean isZip(String url)
	{
		return url.contains(ZIP_EXT);
	}
	
	/**
	 * Obtains the underlying path in a zip file and returns it if it exists.
	 * @param path The full path containing the zip file and the internal path.
	 * @return The underlying path in a zip file if it exists, otherwise {@code null}.
	 */
	private String getInternalPath(String path)
	{
		int pathOffset = 5;
		if(path.length() <= (path.indexOf(ZIP_EXT) + pathOffset))
			return null;
		try
		{
			return path.substring(path.indexOf(ZIP_EXT) + pathOffset);
		} catch(ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}

	/**
	 * Obtains the to a zip file and returns it if it exists, while ignoring the underlying path.
	 * @param path The full path containing the zip file and the internal path.
	 * @return The path to the zip file if it exists, otherwise {@code null}.
	 */
	private String getExternalPath(String path)
	{
		if(!path.contains(ZIP_EXT))
			return null;
		try
		{
			return path.substring(0,path.indexOf(ZIP_EXT)+4);
		} catch(ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}
	
	/*private String fileExtension(String name)
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
	}*/

}
