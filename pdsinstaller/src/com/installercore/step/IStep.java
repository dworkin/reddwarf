package com.installercore.step;

/**
 * An IStep is a singular and contained execution block that can be either
 * nested within another IStep or within a controller instance. 
 * @author Paul Gibler
 */
public interface IStep {
	/**
	 * Runs the IStep. Executes all of the nested IStep objects, and if
	 * successful, will complete without throwing a StepException.
	 * Some IStep classes will not execute anything during a run - in that
	 * case, this function will get called but no result will occur.
	 * @throws StepException A StepException is thrown if there is an error during the execution fo the IStep.
	 */
	void run() throws StepException;
}
