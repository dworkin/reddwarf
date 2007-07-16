/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
public class PasswordDialog extends JDialog implements ActionListener
{

    private static final long serialVersionUID = 1;

    // the name elements
    private JTextField loginField;
    private String login = null;

    // the password elements
    private JPasswordField passField;
    private char [] pass = null;

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
        }

        // regarldess, dispose of the dialog
        setVisible(false);
        dispose();
    }

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

}
