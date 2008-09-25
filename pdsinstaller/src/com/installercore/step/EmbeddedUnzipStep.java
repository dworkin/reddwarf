package com.installercore.step;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.*;

public class EmbeddedUnzipStep implements IStep {

	String resourceLocation;
	String resourceDestination;
	IStringReportCallback myCallback;
	
	/**
	 * 
	 * @param resourceLocation The relative path (relative to THIS class) of the embedded resource.
	 * @param resourceDestination The absolute path to the location on the disk where it's going.
	 * @param callback How the program updates the UI.
	 */
	public EmbeddedUnzipStep(String resourceLocation, String resourceDestination, IStringReportCallback callback)
	{
		this.resourceLocation = resourceLocation;
		this.resourceDestination = resourceDestination;
		myCallback = callback;
	}
	
	/**
	 * @return The entire contents of the embedded file as a string.
	 */
	public void run() throws StepException {
		try
		{
			BufferedOutputStream destination = null;
			final int bufferSize = 2048;
			URL url = this.getClass().getResource(resourceLocation);
			InputStream stream = url.openStream();
			ZipInputStream zstream = new ZipInputStream(stream);
			ZipEntry entry;
			while((entry = zstream.getNextEntry()) != null)
			{
				if(myCallback != null)
					myCallback.report(entry.getName());
				int count = 0;
				byte data[] = new byte[bufferSize];
				File f = new File(resourceDestination + entry.getName());
				String path = f.getAbsolutePath();
				
				if(!entry.isDirectory())
				{
					path = path.substring(0,path.lastIndexOf(File.separator));
				}
				
				f = new File(path);
				f.mkdirs();
				
				if(!entry.isDirectory())
				{
					FileOutputStream ostream = new FileOutputStream(resourceDestination + entry.getName());
					destination = new BufferedOutputStream(ostream, bufferSize);
					while((count = zstream.read(data, 0, bufferSize)) != -1)
					{
						destination.write(data,0,count);
					}
					destination.flush();
					destination.close();
				}
			}
			stream.close();
			if(myCallback != null)
				myCallback.report(null);
		}
		catch (Exception e)
		{
			if(myCallback != null)
				myCallback.report("Extraction error.");
			throw new StepException();
		}
	}

}
