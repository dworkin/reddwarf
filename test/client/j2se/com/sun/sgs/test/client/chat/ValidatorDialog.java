package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
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

import com.sun.sgs.client.ClientAuthenticator;

/**
 * This class provides a Swing GUI for server validation fulfillment.
 */
public class ValidatorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ValidatorDialog.
     */
    public ValidatorDialog(Frame parent, final ClientAuthenticator auth) {
        super(parent, "Login Dialog", true);
        Container c = getContentPane();
        JPanel validationPanel = new JPanel();
        validationPanel.setLayout(new GridLayout(2, 0));
        c.add(validationPanel, BorderLayout.NORTH);
        JButton validateButton = new JButton("CONTINUE");
        c.add(validateButton, BorderLayout.SOUTH);

        validationPanel.add(new JLabel("Login"));
        final JTextField nameField = new JTextField();
        validationPanel.add(nameField);

        validationPanel.add(new JLabel("Password"));
        final JPasswordField passwordField = new JPasswordField();
        validationPanel.add(passwordField);

        validateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
        	String password = new String(passwordField.getPassword());
        	byte[] message = createPasswordMessage(nameField.getText(), password.getBytes());
        	auth.sendMessage(message);
                ValidatorDialog.this.setVisible(false);
                ValidatorDialog.this.getParent().remove(ValidatorDialog.this);
            }
        });

        pack();
        setVisible(true);
    }
    
    byte[] createPasswordMessage(String user, byte[] pass) {
	// TODO
	return null;
    }
}
