package com.installercore;


/**
 * 
 * @author Chris Scalabrini
 */
public class BasicGUIInstallerCallback implements IInstallerCallback {

	private IStepChanger manager;
	
	/**
	 * @param manager The one installermanager being used throughout the installer.
	 */
	public BasicGUIInstallerCallback(IStepChanger manager)
	{
		this.manager = manager;
	}

	public void moveToStep(IInstallerGUIStep step) {
		manager.ChangeStep(step);
	}

}
