package com.installercore.step;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class EmbeddedTextStep implements IStep {

	String text = null;
	String source = null;
	
	/**
	 * @param source  The relative path (relative to THIS class) of the embedded resource.
	 */
	public EmbeddedTextStep(String s)
	{
		source = s;
	}
	
	/**
	 * If there are any errors, it will throw a StepException.
	 */
	public void run() throws StepException {
		try
		{
			URL url = this.getClass().getResource(source);
			InputStream stream = url.openStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			StringBuffer buffer = new StringBuffer();
			while((text = reader.readLine()) != null)
			{
				buffer.append(text);
				buffer.append("\n");
			}
			text = buffer.toString();
		}
		catch(Exception e)
		{
			text = null;
			throw new StepException();
		}
	}
	
	/**
	 * @return The entire contents of the embedded file as a string.
	 */
	public String getReadText()
	{
		return text;
	}

}
