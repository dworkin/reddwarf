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
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    private final JTextField nameField;
    private final JPasswordField passwordField;
    
    private final Semaphore actionSemaphore;

    private PasswordAuthentication passwordAuth;

    /**
     * Creates a new {@code ValidatorDialog} with the given {@code parent}
     * frame.
     *
     * @param parent the parent frame of this dialog
     */
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
    
    /**
     * Returns a {@code PasswordAuthentication} containing the username and
     * password the user has supplied to this dialog, or {@code null} if the
     * dialog was cancelled. This method blocks until the dialog is
     * completed, but may be cancelled by {@link Thread#interrupt()} on the
     * blocked thread.
     * 
     * @return a {@code PasswordAuthentication} containing the username and
     *         password supplied by the user, or {@code null} if the dialog
     *         was cancelled
     * 
     * @see com.sun.sgs.client.simple.SimpleClientListener#getPasswordAuthentication()
     * @see Thread#interrupt()
     */
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
