package com.dsinstaller.view;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.URL;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.dsinstaller.DSInstallerStrings;
import com.installercore.IInstallerCallback;

public class ExitDialog<E> extends Object {

	/**
	 * Calling the constructor displays the exit dialogue.
	 * 
	 * @param callback The callback to the InstallerManager.  Used to exit the program.
	 * @param parent  The parent panel.
	 */
	public ExitDialog(IInstallerCallback callback, JPanel parent)
	{
		StringBuffer titleBuffer = new StringBuffer();
		StringBuffer messageBuffer = new StringBuffer();
		BufferedReader reader = null;

		try
		{
			URL url = this.getClass().getResource(DSInstallerStrings.QuitTextLocation);
			InputStream stream = url.openStream();
			reader = new BufferedReader(new InputStreamReader(stream));
		}
		catch (Exception e)
		{
			this.exit(callback);
		}
		
		appendBuffer(titleBuffer, reader, callback);
		appendBuffer(messageBuffer, reader, callback);
				
		int n = JOptionPane.showConfirmDialog(parent, messageBuffer.toString(), titleBuffer.toString(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if(n == JOptionPane.YES_OPTION)
		{
			this.exit(callback);
		}
	}
	
	/**
	 * Appends the next line from the buffered reader to the string buffer.
	 * @param sb The buffer to add to the string.
	 * @param reader The reader from which to read a line.
	 * @param callback The InstallerManager callback.
	 */
	private void appendBuffer(StringBuffer sb, BufferedReader reader, IInstallerCallback callback)
	{
		try
		{
		String line = reader.readLine();
		if(line == null)
			this.exit(callback);
		sb.append(line);
		}
		catch (Exception e)
		{
			this.exit(callback);
		}
	}
	
	/**
	 * Exits the program.  Nice and easy.
	 * @param callback The callback to the InstallerManager.
	 */
	private void exit(IInstallerCallback callback)
	{
		callback.moveToStep(null);
	}
}


