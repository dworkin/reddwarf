/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.gui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;


/**
 * This is a dialog used to prompt the user for a login name and password.
 */
public abstract class PasswordDialog extends JDialog implements ActionListener
{

    private static final long serialVersionUID = 1;

    // the name elements
    private JTextField loginField;
    private String login = null;

    // the password elements
    private JPasswordField passField;
    private char [] pass = null;
    private boolean cancel = true;
    // the status element
    private JLabel statusField;
    /**
     * Creates an instance of <code>PasswordDialog</code>.
     *
     * @param parent the parent for this dialog
     * @param loginLabel the text label to use for the name field
     * @param passLabel the text label to use for the password field
     */
    public PasswordDialog(Frame parent, String loginLabel, String passLabel) {
        super(parent, "SGS Login", true);

        loginField = new JTextField(20);
        passField = new JPasswordField(20);
        statusField = new JLabel();
        JPanel entryPanel = new JPanel(new GridLayout(3, 2));
        entryPanel.add(new JLabel(loginLabel + ":"));
        entryPanel.add(loginField);
        entryPanel.add(new JLabel(passLabel + ":"));
        entryPanel.add(passField);
        entryPanel.add(statusField);
        
        JButton okButton = new JButton("OK");
        okButton.addActionListener(this);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        setLayout(new BorderLayout());
        add(entryPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Called when the user clicks on the OK or Cancel buttons.
     *
     * @param ae details about what was clicked
     */
    public void actionPerformed(ActionEvent ae) {
        // if the user hit OK, then get the name and password
        if (ae.getActionCommand().equals("OK")) {
            login = loginField.getText();
            pass = passField.getPassword();
            statusField.setText("connecting...");
            connect(login,pass);
            cancel = false;
        }
        else {
          cancel = true;
          setVisible(false);
          dispose();
        }
    }
    
    public void setConnectionFailed(String reason) {
      String connectionFailed = "Connection failed";
      if (reason!=null) {
        connectionFailed = connectionFailed+": "+reason;
      }
      statusField.setText(connectionFailed);
    }
    
    public abstract void connect(String login,char[] pass);
    
    /**
     * Returns the login name that the user provided.
     *
     * @return the login name
     */
    public String getLogin() {
        return login;
    }

    /**
     * Returns the password that the user provided.
     *
     * @return the password
     */
    public char [] getPassword() {
        return pass;
    }

  public boolean isCancel() {
    return cancel;
  }

}
