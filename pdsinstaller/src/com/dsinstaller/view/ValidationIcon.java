package com.dsinstaller.view;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import com.dsinstaller.DSInstallerStrings;

/**
 * {@link ValidationIcon} is a user-interface {@link java.awt.Component} that indicates
 * whether an installer setting is correct or operation has completely successfully. 
 * @author Paul Gibler
 *
 */
public class ValidationIcon extends JLabel {
	
	public enum ValidationType
	{
		SUCCESS, ERROR
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The current {@link ValidationType} of the {@link ValidationIcon}.
	 */
	private ValidationType ValidationType;
	
	/**
	 * Instantiates an {@link ApprovalIcon}.
	 * @param initial The initial {@link ValidationType} of the {@link ValidationIcon}. 
	 */
	public ValidationIcon(ValidationType initial)
	{
		setText("");
		setValidation(initial);
	}
	
	/**
	 * Sets the type of Validation of the icon.
	 * @param rt The new {@link ValidationType} of the {@link ValidationIcon}.
	 */
	@SuppressWarnings("static-access")
	public void setValidation(ValidationType rt)
	{
		if(rt.equals(ValidationType.SUCCESS))
		{
			setIcon(new ImageIcon(getClass().getResource(DSInstallerStrings.SuccessIconLocation)));
			this.ValidationType = rt;
		}
		else if(rt.equals(ValidationType.ERROR))
		{
			setIcon(new ImageIcon(getClass().getResource(DSInstallerStrings.ErrorIconLocation)));
			this.ValidationType = rt;
		}
		this.invalidate();
	}
	
	/**
	 * Returns the current {@link ValidationType} of the {@link ValidationIcon}.
	 * @return The current {@link ValidationType} of the {@link ValidationIcon}.
	 */
	public ValidationType getValidation()
	{
		return this.ValidationType;
	}
}
