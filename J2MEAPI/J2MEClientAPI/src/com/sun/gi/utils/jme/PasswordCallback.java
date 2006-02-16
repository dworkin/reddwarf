/*
 * PasswordCallback.java
 *
 * Created on January 29, 2006, 3:55 PM
 *
 *
 */

package com.sun.gi.utils.jme;

/**
 *
 * @author as93050
 */
public class PasswordCallback implements Callback {
    
    /**
     * @serial
     * @since 1.4
     */
    private String prompt;
    /**
     * @serial
     * @since 1.4
     */
    private boolean echoOn;
    /**
     * @serial
     * @since 1.4
     */
    private char[] inputPassword;

    /**
     * Construct a <code>PasswordCallback</code> with a prompt
     * and a boolean specifying whether the password should be displayed
     * as it is being typed.
     *
     * <p>
     *
     * @param prompt the prompt used to request the password. <p>
     *
     * @param echoOn true if the password should be displayed
     *			as it is being typed.
     *
     * @exception IllegalArgumentException if <code>prompt</code> is null or
     *			if <code>prompt</code> has a length of 0.
     */
    public PasswordCallback(String prompt, boolean echoOn) {
	if (prompt == null || prompt.length() == 0)
	    throw new IllegalArgumentException();

	this.prompt = prompt;
	this.echoOn = echoOn;
    }

    /**
     * Get the prompt.
     *
     * <p>
     *
     * @return the prompt.
     */
    public String getPrompt() {
	return prompt;
    }

    /**
     * Return whether the password
     * should be displayed as it is being typed.
     *
     * <p>
     *
     * @return the whether the password
     *		should be displayed as it is being typed.
     */
    public boolean isEchoOn() {
	return echoOn;
    }

    /**
     * Set the retrieved password.
     *
     * <p> This method makes a copy of the input <i>password</i>
     * before storing it.
     * 
     * <p>
     *
     * @param password the retrieved password, which may be null.
     *
     * @see #getPassword
     */
    public void setPassword(char[] password) {
        if (password == null) {
            this.inputPassword = null;
        } else {
            inputPassword = new char[password.length];
            System.arraycopy(password,0,inputPassword,0,password.length);
        }
    }

    /**
     * Get the retrieved password.
     *
     * <p> This method returns a copy of the retrieved password.
     *
     * <p>
     *
     * @return the retrieved password, which may be null.
     *
     * @see #setPassword
     */
    public char[] getPassword() {
	if (inputPassword == null) {
            return null;
        } else {
            char[] copiedPassword = new char[inputPassword.length];
            System.arraycopy(inputPassword,0,copiedPassword,0,inputPassword.length);
            return copiedPassword;
        }
    }

    /**
     * Clear the retrieved password.
     */
    public void clearPassword() {
	if (inputPassword != null) {
	    for (int i = 0; i < inputPassword.length; i++)
		inputPassword[i] = ' ';
	}
    }
    
}
