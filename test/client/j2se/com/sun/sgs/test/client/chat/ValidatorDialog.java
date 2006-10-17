package com.sun.sgs.test.client.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.sun.sgs.client.ClientCredentials;
import com.sun.sgs.client.PasswordCredentials;

/**
 * This class provides a Swing GUI for server validation fulfillment.
 */
public class ValidatorDialog extends JDialog
	implements Future<ClientCredentials>
{

    private static final long serialVersionUID = 1L;

    private final Semaphore semaphore = new Semaphore(0);
    private PasswordCredentials credentials;

    /**
     * Constructs a new ValidatorDialog.
     */
    public ValidatorDialog(Frame parent) {
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
        	credentials = new PasswordCredentials(nameField.getText(),
        		new String(passwordField.getPassword()).getBytes());
                ValidatorDialog.this.setVisible(false);
                ValidatorDialog.this.getParent().remove(ValidatorDialog.this);
                semaphore.release();
            }
        });

        pack();
        setVisible(true);
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
	return false;
    }

    public ClientCredentials get() throws InterruptedException, ExecutionException {
	semaphore.acquire();
	return credentials;
    }

    public ClientCredentials get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
	if (semaphore.tryAcquire(timeout, unit)) {
	    return credentials;
	}
	return null;
    }

    public boolean isCancelled() {
	return false;
    }

    public boolean isDone() {
	return (credentials != null);
    }
}
