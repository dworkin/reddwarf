/*****************************************************************************
     * Copyright (c) 2006 Sun Microsystems, Inc.  All Rights Reserved.
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions are met:
     *
     * - Redistribution of source code must retain the above copyright notice,
     *   this list of conditions and the following disclaimer.
     *
     * - Redistribution in binary form must reproduce the above copyright notice,
     *   this list of conditions and the following disclaimer in the documentation
     *   and/or other materails provided with the distribution.
     *
     * Neither the name Sun Microsystems, Inc. or the names of the contributors
     * may be used to endorse or promote products derived from this software
     * without specific prior written permission.
     *
     * This software is provided "AS IS," without a warranty of any kind.
     * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
     * ANY IMPLIED WARRANT OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
     * NON-INFRINGEMEN, ARE HEREBY EXCLUDED.  SUN MICROSYSTEMS, INC. ("SUN") AND
     * ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS
     * A RESULT OF USING, MODIFYING OR DESTRIBUTING THIS SOFTWARE OR ITS
     * DERIVATIVES.  IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
     * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
     * INCIDENTAL OR PUNITIVE DAMAGES.  HOWEVER CAUSED AND REGARDLESS OF THE THEORY
     * OF LIABILITY, ARISING OUT OF THE USE OF OUR INABILITY TO USE THIS SOFTWARE,
     * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
     *
     * You acknowledge that this software is not designed or intended for us in
     * the design, construction, operation or maintenance of any nuclear facility
     *
     *****************************************************************************/


package com.sun.gi.apps.chattest.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.ChoiceCallback;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * This class provides a Swing GUI for server validation fulfillment.  When connecting to a server application
 *  via a UserManager, the UserManager will attempt to validate the user based on the applications validation
 *  settings as specified in its deployment descriptor.  In the case of the CommTest application, a name and a 
 *  password are required.
 *
 */

//@SuppressWarnings("serial")
public class ValidatorDialog extends JDialog {
	
	Callback[] callbacks;										// The array of javax.security.auth.callbacks.CallBacks 
	
	List<Component> dataFields = new ArrayList<Component>();	// an array of UI components
																// that parallel callbacks[] in size and order.

	/**
	 * Constructs a new ValidatorDialog.  The dialog iterates through the CallBack array
	 * and displays the appropriate UI based on the type of CallBack.  In the case of CommTest,
	 * the CallBacks are of type NameCallBack and PasswordCallBack.  This causes both a "username" 
	 * textfield, and a "password" input field to be rendered on the dialog. 
	 * 
	 * @param parent		the dialog's parent frame
	 * @param cbs			an array of CallBacks
	 */
	public ValidatorDialog(Frame parent,Callback[] cbs){
		super(parent,"Validation Information Required",true);
		callbacks = cbs;
		Container c = getContentPane();
		JPanel validationPanel = new JPanel();
		validationPanel.setLayout(new GridLayout(2,0));
		c.add(validationPanel,BorderLayout.NORTH);
		JButton validateButton = new JButton("CONTINUE");
		c.add(validateButton,BorderLayout.SOUTH);
		
		// when pressed, set the data from the UI components to the matching CallBacks.
		validateButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				transcribeToCallbacks();
				ValidatorDialog.this.setVisible(false);
				ValidatorDialog.this.getParent().remove(ValidatorDialog.this);				
			}
		});
		
		// Iterate through the javax.security.auth.callback.CallBacks 
		// and render the appropriate UI accordingly.
		// For each CallBack, the matching UI Component is stored in
		// the dataFields array.  The order is important, as they will 
		// be retrieved along side their matching CallBack.
		for(Callback cb : cbs){
			if (cb instanceof ChoiceCallback){
				ChoiceCallback ccb = (ChoiceCallback)cb;
				validationPanel.add(new JLabel(ccb.getPrompt()));
				JComboBox combo = new JComboBox(ccb.getChoices());
				combo.setSelectedItem(ccb.getDefaultChoice());
				validationPanel.add(combo);
				dataFields.add(combo);
			} else if (cb instanceof ConfirmationCallback) {
				ConfirmationCallback ccb = (ConfirmationCallback)cb;
				validationPanel.add(new JLabel(ccb.getPrompt()));
				JComboBox combo = new JComboBox(ccb.getOptions());
				combo.setSelectedItem(ccb.getDefaultOption());
				validationPanel.add(combo);
				dataFields.add(combo);
			} else if (cb instanceof NameCallback){
				NameCallback ncb= (NameCallback)cb;
				validationPanel.add(new JLabel(ncb.getPrompt()));
				JTextField nameField = new JTextField(ncb.getDefaultName());
				validationPanel.add(nameField);
				dataFields.add(nameField);
			} else if (cb instanceof PasswordCallback){
				PasswordCallback ncb= (PasswordCallback)cb;
				validationPanel.add(new JLabel(ncb.getPrompt()));
				JPasswordField passwordField = new JPasswordField();
				validationPanel.add(passwordField);
				dataFields.add(passwordField);
			} else if (cb instanceof TextInputCallback){
				TextInputCallback tcb = (TextInputCallback)cb;
				validationPanel.add(new JLabel(tcb.getPrompt()));
				JTextField textField = new JTextField(tcb.getDefaultText());
				validationPanel.add(textField);
				dataFields.add(textField);				
			} else if (cb instanceof TextOutputCallback ){
				TextOutputCallback tcb = (TextOutputCallback)cb;
				validationPanel.add(new JLabel(tcb.getMessage()));	
				
			}
		}
		pack();
		setVisible(true);
	}

	/**
	 * Called when the validation button is pressed.  It iterates through the array of CallBacks
	 * and takes the data from the matching UI component and sets it in the CallBack.
	 *
	 */
	protected void transcribeToCallbacks() {
		Iterator iter = dataFields.iterator();
		for(Callback cb : callbacks){
			if (cb instanceof ChoiceCallback){
				//note this is really wrong, should allow for multiple select
				ChoiceCallback ccb = (ChoiceCallback)cb;
				JComboBox combo = (JComboBox) iter.next();
				ccb.setSelectedIndex(combo.getSelectedIndex());				
			} else if (cb instanceof ConfirmationCallback) {
				ConfirmationCallback ccb = (ConfirmationCallback)cb;
				JComboBox combo = (JComboBox) iter.next();
				ccb.setSelectedIndex(combo.getSelectedIndex());
			} else if (cb instanceof NameCallback){
				NameCallback ncb= (NameCallback)cb;				
				JTextField nameField = (JTextField)iter.next();
				ncb.setName(nameField.getText());
			} else if (cb instanceof PasswordCallback){
				PasswordCallback ncb= (PasswordCallback)cb;
				JPasswordField passwordField = (JPasswordField)iter.next();
				ncb.setPassword(passwordField.getPassword());
			} else if (cb instanceof TextInputCallback){
				TextInputCallback tcb = (TextInputCallback)cb;				
				JTextField textField = (JTextField)iter.next();
				tcb.setText(textField.getText());			
			} else if (cb instanceof TextOutputCallback ){
				// no response required				
			}
		}
		
	}
}
