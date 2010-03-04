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

package com.installercore;

public class InstallerManager implements IStepChanger{

	private InstallerWindow window;
	private IInstallerGUIStep currentGUIStep;
	
	private IIManagerCallback exitCallback;
	
	/**
	 * @param onExit What to do upon exiting.
	 * @param firstStep The starting point for the InstallerManager.
	 */
	public InstallerManager(IIManagerCallback onExit, IInstallerGUIStep firstStep)
	{	
		exitCallback = onExit;
		ChangeStep(firstStep);
	}
	
	/**
	 * @param step The next step.  If the installer manager wasn't called with
	 * a GUI step as the first step, it will throw a RuntimeException.
	 */
	public void ChangeStep(IInstallerGUIStep step)
	{
		
		if(window == null)
			window = new InstallerWindow();
		
		if(step == null)
		{
			window.setVisible(false);
			if(exitCallback != null)
			{
				exitCallback.CallEvent(null);
			}
			else
			{
				System.exit(0);
			}
		}
		else
		{
			window.setVisible(true);
			step.setNextStepCallback(new BasicGUIInstallerCallback(this));
			step.stepRequested(currentGUIStep);
			currentGUIStep = step;
			window.setCenterPanel(currentGUIStep.getPanel());
		}	
	}
}
