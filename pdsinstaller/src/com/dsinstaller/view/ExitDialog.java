/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

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


