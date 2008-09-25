package com.installercore;

/**
 * @author Chris Scalabrini
 * 
 */
public interface IInstallerCallback {
	
	/**
	 * @param step The next installer-step to move to.  This allows users to move
	 * between steps, both forward and backward.
	 */
	public void moveToStep(IInstallerGUIStep step);
}
