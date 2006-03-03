/*    
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
  California 95054, U.S.A. All rights reserved.

 Sun Microsystems, Inc. has intellectual property rights relating to 
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights 
 may include one or more of the U.S. patents listed at 
 http://www.sun.com/patents and one or more additional patents or pending 
 patent applications in the U.S. and in other countries.

 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable 
 provisions of the FAR and its supplements.

 This distribution may include materials developed by third parties.

 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered 
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.

 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.

 Products covered by and information contained in this service manual are 
 controlled by U.S. Export Control laws and may be subject to the export 
 or import laws in other countries. Nuclear, missile, chemical biological 
 weapons or nuclear maritime end uses or end users, whether direct or 
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists, 
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.

 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS, 
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF 
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, 
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE 
 LEGALLY INVALID.

 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.

 Sun Microsystems, Inc. détient les droits de propriété intellectuels 
 relatifs à la technologie incorporée dans le produit qui est décrit dans 
 ce document. En particulier, et ce sans limitation, ces droits de 
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets 
 supplémentaires ou les applications de brevet en attente aux Etats - 
 Unis et dans les autres pays.

 Cette distribution peut comprendre des composants développés par des 
 tierces parties.

 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique 
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans 
 d'autres pays.

 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et 
 licenciée exlusivement par X/Open Company, Ltd.

 see above Les produits qui font l'objet de ce manuel d'entretien et les 
 informations qu'il contient sont regis par la legislation americaine en 
 matiere de controle des exportations et peuvent etre soumis au droit 
 d'autres pays dans le domaine des exportations et importations. 
 Les utilisations finales, ou utilisateurs finaux, pour des armes 
 nucleaires, des missiles, des armes biologiques et chimiques ou du 
 nucleaire maritime, directement ou indirectement, sont strictement 
 interdites. Les exportations ou reexportations vers des pays sous embargo 
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion 
 d'exportation americaines, y compris, mais de maniere non exclusive, la 
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une 
 facon directe ou indirecte, aux exportations des produits ou des services 
 qui sont regi par la legislation americaine en matiere de controle des 
 exportations et la liste de ressortissants specifiquement designes, sont 
 rigoureusement interdites.

 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS, 
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES, 
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE 
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE 
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
 */
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
