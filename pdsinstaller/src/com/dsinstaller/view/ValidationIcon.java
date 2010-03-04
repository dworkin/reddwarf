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
