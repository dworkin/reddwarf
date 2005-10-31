package com.sun.gi.comm.test;

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

@SuppressWarnings("serial")
public class ValidatorDialog extends JDialog{
	Callback[] callbacks;
	List<Component> dataFields = new ArrayList<Component>();

	public ValidatorDialog(Frame parent,Callback[] cbs){
		super(parent,"Validation Information Required",true);
		callbacks = cbs;
		Container c = getContentPane();
		JPanel validationPanel = new JPanel();
		validationPanel.setLayout(new GridLayout(2,0));
		c.add(validationPanel,BorderLayout.NORTH);
		JButton validateButton = new JButton("CONTINUE");
		c.add(validateButton,BorderLayout.SOUTH);
		validateButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				transcribeToCallbacks();
				ValidatorDialog.this.setVisible(false);
				ValidatorDialog.this.getParent().remove(ValidatorDialog.this);				
			}
		});
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
		setVisible(true);
	}

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
