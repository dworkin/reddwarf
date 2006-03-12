
/*
 * PasswordDialog.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Feb 17, 2006	 7:43:01 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

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
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class PasswordDialog extends JDialog implements ActionListener
{

    //
    private JTextField loginField;
    private String login = null;

    //
    private JPasswordField passField;
    private char [] pass = null;

    /**
     *
     */
    public PasswordDialog(Frame parent, String loginLabel, String passLabel) {
        super(parent, "SGS Login", true);

        loginField = new JTextField(30);
        passField = new JPasswordField(30);

        JPanel entryPanel = new JPanel(new GridLayout(2, 2));
        entryPanel.add(new JLabel(loginLabel + ":"));
        entryPanel.add(loginField);
        entryPanel.add(new JLabel(passLabel + ":"));
        entryPanel.add(passField);

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

    public void actionPerformed(ActionEvent ae) {
        System.out.println("Button is: " + ae.getActionCommand());
        
        if (ae.getActionCommand().equals("OK")) {
            login = loginField.getText();
            pass = passField.getPassword();
        }

        setVisible(false);
        dispose();
    }

    /**
     *
     */
    public String getLogin() {
        return login;
    }

    /**
     *
     */
    public char [] getPassword() {
        return pass;
    }

}
