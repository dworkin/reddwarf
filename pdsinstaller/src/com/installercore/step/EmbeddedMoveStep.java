package com.installercore.step;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import com.dsinstaller.DSInstallerStrings;

public class EmbeddedMoveStep implements IStep {

	String source = null;
	String destination = null;
	
	/**
	 * 
	 * @param source  The relative path (relative to THIS class) of the embedded resource.
	 * @param destination The absolute path to the location on the disk where it's going.
	 */
	public EmbeddedMoveStep(String source, String destination)
	{
		this.source = DSInstallerStrings.targetLocation + source;
		this.destination = destination;
	}
	
	/**
	 * If there are any errors, it will throw a StepException.
	 */
	public void run() throws StepException {
		try
		{
			final int bufferSize = 2048;
			URL url = this.getClass().getResource(source);
			InputStream stream = url.openStream();
			
			File f = new File(destination);
			String path = f.getAbsolutePath();
			path = path.substring(0,path.lastIndexOf(File.separator));
			f = new File(path);
			f.mkdirs();
			
			byte data[] = new byte[bufferSize];
			int count = 0;
			
			FileOutputStream ostream = new FileOutputStream(destination);
			BufferedOutputStream output  = new BufferedOutputStream(ostream, bufferSize);
			while((count = stream.read(data, 0, bufferSize)) != -1)
			{
				output.write(data,0,count);
			}
			output.flush();
			output.close();
			stream.close();
		}
		catch (Exception e)
		{
			throw new StepException();
		}	
	}

}
