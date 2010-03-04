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

import java.awt.Color;
import java.awt.GridBagLayout;
import javax.swing.JPanel;

import com.installercore.IInstallerCallback;
import com.installercore.IInstallerGUIStep;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.BorderLayout;
import javax.swing.JButton;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;

import com.dsinstaller.DSInstallerStrings;
import com.dsinstaller.view.ValidationIcon;
import com.dsinstaller.view.ValidationIcon.ValidationType;
import com.installercore.step.EmbeddedTextStep;
import com.installercore.step.StepException;

public class CompletePanel extends JPanel implements IInstallerGUIStep, ActionListener {

	IInstallerCallback myCallback;
	IInstallerGUIStep lastStep;
	private static final long serialVersionUID = 1L;
	private JPanel buttonPanel = null;
	private JPanel middlePanel = null;
	private JButton cancelButton = null;
	private JPanel jPanel = null;
	private JLabel textLabel = null;
	private ValidationIcon successIconLabel = null;

	public JPanel getPanel() {
		return this;
	}

	/**
	 * You don't need to touch this one.
	 */
	public void setNextStepCallback(IInstallerCallback callback) {
		myCallback = callback;
	}

	/**
	 * You don't need to touch this one.
	 */
	public void stepRequested(IInstallerGUIStep previousStep) {
		if(lastStep == null)
			lastStep = previousStep;
	}

	/**
	 * This is the default constructor.  This is called upon a successful installation.
	 */
	public CompletePanel() {
		super();
		initialize();
		successIconLabel.setValidation(ValidationType.SUCCESS);
		EmbeddedTextStep step = new EmbeddedTextStep(DSInstallerStrings.InstallSuccessfulTextLocation);
		try
		{
			step.run();
			textLabel.setText(step.getReadText());
		}
		catch (StepException e)
		{}
	}
	
	/**
	 * This constructor is called when something goes horribly, horribly wrong.
	 * @param s The error message.
	 */
	public CompletePanel(String s)
	{
		super();
		initialize();
		successIconLabel.setValidation(ValidationType.ERROR);
		EmbeddedTextStep step = new EmbeddedTextStep(DSInstallerStrings.InstallErrorTextLocation);
		try
		{
			step.run();
			textLabel.setText(step.getReadText() + s);
		}
		catch (StepException e)
		{
			textLabel.setText("Project Darkstar could not install successfully.  " + s);
		}
		
	}
	
	

	/**
	 * This method initializes this
	 * 
	 * @return void
	 */
	private void initialize() {
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setVgap(2);
		this.setLayout(borderLayout);
		this.setSize(630, 415);
		this.setBackground(new Color(255,255,255));
		this.add(getButtonPanel(), BorderLayout.SOUTH);
		this.add(getMiddlePanel(), BorderLayout.CENTER);
		cancelButton.addActionListener(this);
	}

	/**
	 * This method initializes buttonPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getButtonPanel() {
		if (buttonPanel == null) {
			FlowLayout flowLayout = new FlowLayout();
			flowLayout.setAlignment(java.awt.FlowLayout.RIGHT);
			flowLayout.setVgap(23);
			flowLayout.setHgap(5);
			GridLayout gridLayout = new GridLayout();
			gridLayout.setRows(2);
			buttonPanel = new JPanel();
			buttonPanel.setLayout(flowLayout);
			buttonPanel.setBackground(new Color(114,188,217));
			buttonPanel.setPreferredSize(new Dimension(200, 76));
			buttonPanel.add(getCancelButton(), null);
		}
		return buttonPanel;
	}

	/**
	 * This method initializes middlePanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getMiddlePanel() {
		if (middlePanel == null) {
			GridBagConstraints gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 0;
			middlePanel = new JPanel();
			middlePanel.setPreferredSize(new Dimension(250, 120));
			middlePanel.setLayout(new GridBagLayout());
			middlePanel.setBackground(new Color(6,88,156));
			middlePanel.add(getJPanel(), gridBagConstraints);
		}
		return middlePanel;
	}

	/**
	 * This method initializes cancelButton	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getCancelButton() {
		if (cancelButton == null) {
			cancelButton = new JButton();
			cancelButton.setName("cancelButton");
			cancelButton.setText("Finish");
			cancelButton.setPreferredSize(new Dimension(115, 30));
		}
		return cancelButton;
	}

	/**
	 * This method initializes jPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getJPanel() {
		if (jPanel == null) {
			GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
			gridBagConstraints3.gridx = 0;
			gridBagConstraints3.gridy = 0;
			successIconLabel = new ValidationIcon(ValidationType.ERROR);
			GridBagConstraints gridBagConstraints2 = new GridBagConstraints();
			gridBagConstraints2.gridx = 1;
			gridBagConstraints2.gridy = 1;
			GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
			gridBagConstraints1.gridx = 1;
			gridBagConstraints1.gridy = 0;
			textLabel = new JLabel();
			textLabel.setText("Congratulations!  You have successfully installed Project Darkstar!");
			textLabel.setForeground(Color.white);
			textLabel.setBackground(new Color(6, 88, 156));
			jPanel = new JPanel();
			jPanel.setLayout(new GridBagLayout());
			jPanel.setPreferredSize(new Dimension(450, 300));
			jPanel.setBackground(new Color(6, 88, 156));
			jPanel.add(textLabel, gridBagConstraints1);
			jPanel.add(successIconLabel, gridBagConstraints3);
		}
		return jPanel;
	}

	public void actionPerformed(ActionEvent arg0) {
		if(ButtonTracker.mouseClicked(arg0, cancelButton))
		{
			myCallback.moveToStep(null);
		}
	}
}  //  @jve:decl-index=0:visual-constraint="10,10"
