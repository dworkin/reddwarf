/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.chat.client;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.PasswordAuthentication;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

/**
 * Simple GUI component to accept a username and password from a user.
 */
public class LoginDialog extends JDialog
	implements ActionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The text field to accept the user name. */
    private final JTextField nameField;

    /** The password input field. */
    private final JPasswordField passwordField;

    /** The {@code LoginFuture} that gets the login credentials. */
    private final LoginFuture loginFuture;

    /**
     * Creates a new {@code LoginDialog} with the given {@code parent}
     * frame.
     *
     * @param parent the parent frame of this dialog
     */
    public LoginDialog(Frame parent) {
        super(parent, "Login Dialog", true);
        setLocationByPlatform(true);

        Container c = getContentPane();
        JPanel validationPanel = new JPanel();
        validationPanel.setLayout(new GridLayout(2, 0));
        c.add(validationPanel, BorderLayout.NORTH);
        JButton validateButton = new JButton("Login");
        validateButton.addActionListener(this);
        c.add(validateButton, BorderLayout.SOUTH);

        validationPanel.add(new JLabel("Login"));
        nameField = new JTextField();
        nameField.addActionListener(this);
        validationPanel.add(nameField);

        validationPanel.add(new JLabel("Password"));
        passwordField = new JPasswordField();
        passwordField.addActionListener(this);
        validationPanel.add(passwordField);

        loginFuture = new LoginFuture();
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        addWindowListener(new CancelWindowListener(this));

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
    public Future<PasswordAuthentication> requestLogin() {
        return loginFuture;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation sets the {@code LoginFuture} with the credentials
     * from the user and closes the dialog.
     */
    public void actionPerformed(ActionEvent e) {
	PasswordAuthentication auth = new PasswordAuthentication(
	    nameField.getText(), passwordField.getPassword());

        passwordField.setText(null);

        if (! loginFuture.set(auth)) {
            // Couldn't set the future, so clear the password ourselves
            char[] passwd = auth.getPassword();
            Arrays.fill(passwd, '\0');
            dispose();
        }
    }

    /**
     * Closes this dialog.
     */
    private void close() {
        passwordField.setText(null);
        getParent().remove(this);
    }

    /**
     * Cancels this dialog.
     *
     * @throws CancellationException if this dialog was already done
     * @see Future#cancel
     */
    public void cancel() {
        loginFuture.cancel(true);
    }

    /**
     * Represents the future result of the login credentials entered
     * by the user.
     *
     * @see Future
     */
    private final class LoginFuture
        implements Future<PasswordAuthentication>
    {
        /**
         * The synchronization blocker for threads awaiting the completion
         * of this future.
         */
        private final CountDownLatch latch = new CountDownLatch(1);

        /** The result to return as this future's value, or null if not set. */
        private volatile PasswordAuthentication passwordAuth = null;

        /** Whether this future has been cancelled. */
        private boolean cancelled = false;

        /**
         * Constructs a new {@code LoginFuture} for this {@code LoginDialog}.
         */
        private LoginFuture() {
            // empty
        }

        /**
         * Throws {@link CancellationException} if this {@code LoginFuture}
         * has been cancelled.
         *
         * @throws CancellationException if this {@code LoginFuture} has
         *         been cancelled
         */
        private void checkCancelled() throws CancellationException {
            if (isCancelled()) {
                throw new CancellationException();
            }
        }

        /**
         * Sets the return value for this {@code LoginFuture}.
         *
         * @param auth the value to set for this {@code LoginFuture}
         * @return {@code true} if the value was set, otherwise false if
         *         this future was already {@linkplain #isDone done}
         */
        boolean set(PasswordAuthentication auth) {
            synchronized (this) {
                if (isCancelled()) {
                    return false;
                }
                this.passwordAuth = auth;
            }
            setDone();
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (this) {
                if ((! mayInterruptIfRunning) || isDone()) {
                    // This future is running upon creation
                    return false;
                }

                cancelled = true;
            }
            setDone();
            return true;
        }

        /**
         * Marks this future as done and performs related cleanup.
         */
        private void setDone() {
            latch.countDown();
            dispose();
        }

        /**
         * {@inheritDoc}
         */
        public PasswordAuthentication get()
                throws InterruptedException, ExecutionException
        {
            checkCancelled();
            latch.await();
            checkCancelled();
            return passwordAuth;
        }

        /**
         * {@inheritDoc}
         */
        public PasswordAuthentication get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                       TimeoutException
        {
            checkCancelled();
            if (! latch.await(timeout, unit)) {
                throw new TimeoutException();
            }
            checkCancelled();
            return passwordAuth;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isCancelled() {
            synchronized (this) {
                return cancelled;
            }
        }

        /**
         * {@inheritDoc}
         */
        public boolean isDone() {
            synchronized (this) {
                return (cancelled || (passwordAuth != null));
            }
        }
    }

    /**
     * Listener that cancels login when the dialog is closed.
     */
    private static final class CancelWindowListener extends WindowAdapter {
        private final LoginDialog dialog;

        /**
         * Creates a new {@code CancelWindowListener} for the given
         * {@code LoginDialog}.
         *
         * @param client the client to notify on windowClosing
         */
        CancelWindowListener(LoginDialog dialog) {
            this.dialog = dialog;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void windowClosing(WindowEvent e) {
            dialog.close();
        }
    }
}
