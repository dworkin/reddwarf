package com.installercore;
import javax.swing.JPanel;

/**
 * @author Chris Scalabrini
 * 
 */

public interface IInstallerGUIStep {

	/**
	 * @return The JPanel to be displayed in the main window.  The class is responsible for all
	 * the GUI behavior of the panel.
	 */
	public JPanel getPanel();
	
	/**
	 * @param callback When the single method from this interface is called, the manager is
	 * informed of the request to move forward.
	 */
	public void setNextStepCallback(IInstallerCallback callback);
	
	/**
	 * This is called by the InstallerManager when this step is requested.  Note that by the nature
	 * of an installer program, this step is always called first with the previous step in the line.
	 * However, on subsequent calls, the previous step is likely from further up the chain.
	 * @param previousStep The previous step, in case the step includes a "previous" button.
	 * If there is no previous step, then it will be null.
	 */
	public void stepRequested(IInstallerGUIStep previousStep);
}
