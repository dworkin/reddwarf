package com.installercore.step;

import javax.swing.text.JTextComponent;

public class TextFieldStringReportCallback implements IStringReportCallback {

	JTextComponent myComponent;
	
	/**
	 * @param field The text field to update.
	 */
	public TextFieldStringReportCallback(JTextComponent field)
	{
		myComponent = field;
	}
	
	/**
	 * It is explicitly documented that JTextComponent.setText(String s) is 100% thread safe.
	 * This makes our job so much easier.
	 */
	public void report(String s) {
		myComponent.setText(s);
	}

}
