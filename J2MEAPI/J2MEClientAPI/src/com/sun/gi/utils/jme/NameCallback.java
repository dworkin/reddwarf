/*
 * NameCallback.java
 *
 * Created on January 29, 2006, 3:54 PM
 *
 *
 */

package com.sun.gi.utils.jme;

/**
 *
 * @author as93050
 */
public class NameCallback implements Callback {
    
   private String prompt;
    /**
     * @serial
     * @since 1.4
     */
    private String defaultName;
    /**
     * @serial
     * @since 1.4
     */
    private String inputName;

    /**
     * Construct a <code>NameCallback</code> with a prompt.
     *
     * <p>
     *
     * @param prompt the prompt used to request the name.
     *
     * @exception IllegalArgumentException if <code>prompt</code> is null
     *			or if <code>prompt</code> has a length of 0.
     */
    public NameCallback(String prompt) {
	if (prompt == null || prompt.length() == 0)
	    throw new IllegalArgumentException();
	this.prompt = prompt;
    }

    /**
     * Construct a <code>NameCallback</code> with a prompt
     * and default name.
     *
     * <p>
     *
     * @param prompt the prompt used to request the information. <p>
     *
     * @param defaultName the name to be used as the default name displayed 
     *			with the prompt.
     *
     * @exception IllegalArgumentException if <code>prompt</code> is null,
     *			if <code>prompt</code> has a length of 0,
     *			if <code>defaultName</code> is null,
     *			or if <code>defaultName</code> has a length of 0.
     */
    public NameCallback(String prompt, String defaultName) {
	if (prompt == null || prompt.length() == 0 ||
	    defaultName == null || defaultName.length() == 0)
	    throw new IllegalArgumentException();

	this.prompt = prompt;
	this.defaultName = defaultName;
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
     * Get the default name.
     *
     * <p>
     *
     * @return the default name, or null if this <code>NameCallback</code>
     *		was not instantiated with a <code>defaultName</code>.
     */
    public String getDefaultName() {
	return defaultName;
    }

    /**
     * Set the retrieved name.
     *
     * <p>
     *
     * @param name the retrieved name (which may be null).
     *
     * @see #getName
     */
    public void setName(String name) {
	this.inputName = name;
    }

    /**
     * Get the retrieved name.
     *
     * <p>
     *
     * @return the retrieved name (which may be null)
     *
     * @see #setName
     */
    public String getName() {
	return inputName;
    }
    
}
