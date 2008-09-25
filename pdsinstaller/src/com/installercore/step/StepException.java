package com.installercore.step;

@SuppressWarnings("serial")
public class StepException extends Exception {
	
	/**
	 * Constructor for StepExceptionType
	 * @param stepExceptionType The type of the StepException
	 */
	public StepException()
	{
		super("No information provided.");
	}
	
	/**
	 * Constructor for StepExceptionType
	 * @param stepExceptionType The type of the StepException
	 * @param message The message containing more information about the failure.
	 */
	public StepException(String message)
	{
		super(message);
	}
}
