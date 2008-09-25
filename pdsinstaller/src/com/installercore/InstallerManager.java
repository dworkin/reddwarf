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
