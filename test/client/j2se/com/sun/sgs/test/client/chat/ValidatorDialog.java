package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.PasswordAuthentication;
import java.util.concurrent.Semaphore;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Swing GUI for simple SGS login.
 */
public class ValidatorDialog extends JDialog
	implements ActionListener
{
    private static final long serialVersionUID = 1L;

    private final JTextField nameField;
    private final JPasswordField passwordField;
    
    private final Semaphore actionSemaphore;

    private PasswordAuthentication passwordAuth;

    public ValidatorDialog(Frame parent) {
        super(parent, "Login Dialog", true);
        
        actionSemaphore = new Semaphore(0);
        
        Container c = getContentPane();
        JPanel validationPanel = new JPanel();
        validationPanel.setLayout(new GridLayout(2, 0));
        c.add(validationPanel, BorderLayout.NORTH);
        JButton validateButton = new JButton("Login");
        validateButton.addActionListener(this);
        c.add(validateButton, BorderLayout.SOUTH);

        validationPanel.add(new JLabel("Login"));
        nameField = new JTextField();
        validationPanel.add(nameField);

        validationPanel.add(new JLabel("Password"));
        passwordField = new JPasswordField();
        validationPanel.add(passwordField);

        pack();
        setVisible(true);
    }
    
    public PasswordAuthentication getPasswordAuthentication() {
	try {
	    actionSemaphore.acquire();
        } catch (InterruptedException e) {
	    return null;
        }
	return passwordAuth;
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
	passwordAuth = new PasswordAuthentication(
		nameField.getText(), passwordField.getPassword());
	actionSemaphore.release();
	passwordField.setText(null);
        setVisible(false);
        getParent().remove(ValidatorDialog.this);
    }
}
