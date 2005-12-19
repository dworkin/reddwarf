/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

package com.sun.multicast.reliable.applications.slinger;

import java.beans.PropertyVetoException;
import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import com.sun.multicast.reliable.applications.slinger.Spinner;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
public class NumericSpinner extends Spinner implements java.io.Serializable {

    /**
     * Constructs an empty NumericSpinner.
     */
    public NumericSpinner() {
        try {
            setIncrement(1);
        } catch (PropertyVetoException e) {}

        min = 0;
        max = 10;
    }

    /**
     * Sets the minimum value the spinner may have.
     * @param i the new minimum value
     * @see Spinner#getMin
     * @see #setMax
     * 
     * Overriden here to set the size of the text area.
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setMin(int i) throws PropertyVetoException {
        super.setMin(i);

        if (added) {
            int oldValue = textWidth;

            textWidth = Math.max(Integer.toString(min).length(), 
                                 Integer.toString(max).length());
        }
    }

    /**
     * Sets the maximum value the spinner may have.
     * @param i the new maximum value
     * @see Spinner#getMax
     * @see #setMin
     * 
     * Overriden here to set the size of the text area.
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setMax(int i) throws PropertyVetoException {
        super.setMax(i);

        if (added) {
            int oldValue = textWidth;

            textWidth = Math.max(Integer.toString(min).length(), 
                                 Integer.toString(max).length());
        }
    }

    /**
     * Sets the value to increment/decrement the Spinner by.
     * @param int i the increment/decrement value
     * @see #getIncrement
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setIncrement(int i) throws PropertyVetoException {
        Integer oldValue = new Integer(increment);
        Integer newValue = new Integer(i);

        vetos.fireVetoableChange("increment", oldValue, newValue);

        increment = i;

        changes.firePropertyChange("increment", oldValue, newValue);
    }

    /**
     * Gets the increment/decrement value.
     * @return the increment/decrement value
     * @see #setIncrement
     */
    public int getIncrement() {
        return increment;
    }

    /**
     * Gets the current text from the Spinner.
     * @return the text of the currently selected Spinner value
     */
    public String getCurrentText() {
        return Integer.toString(current);
    }

    /**
     * Tells this component that it has been added to a container.
     * This is a standard Java AWT method which gets called by the AWT when
     * this component is added to a container. Typically, it is used to
     * create this component's peer.
     * Here it's used to set maximum text width and note the text of the
     * current selection.
     * 
     * @see java.awt.Container#removeNotify
     */
    public void addNotify() {
        textWidth = Math.max(Integer.toString(min).length(), 
                             Integer.toString(max).length());
        text = Integer.toString(current);

        super.addNotify();
        updateText(false);
    }

    /**
     * Adds a listener for all property change events.
     * @param listener the listener to add
     * @see #removePropertyChangeListener
     */
    public synchronized void addPropertyChangeListener(
	PropertyChangeListener listener) {

        super.addPropertyChangeListener(listener);
        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for all property change events.
     * @param listener the listener to remove
     * @see #addPropertyChangeListener
     */
    public synchronized void removePropertyChangeListener(
	PropertyChangeListener listener) {

        super.removePropertyChangeListener(listener);
        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a listener for all vetoable property change events.
     * @param listener the listener to add
     * @see #removeVetoableChangeListener
     */
    public synchronized void addVetoableChangeListener(
	VetoableChangeListener listener) {

        super.addVetoableChangeListener(listener);
        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a listener for all vetoable property change events.
     * @param listener the listener to remove
     * @see #addVetoableChangeListener
     */
    public synchronized void removeVetoableChangeListener(
	VetoableChangeListener listener) {

        super.removeVetoableChangeListener(listener);
        vetos.removeVetoableChangeListener(listener);
    }

    private java.beans.VetoableChangeSupport vetos = 
        new java.beans.VetoableChangeSupport(this);
    private java.beans.PropertyChangeSupport changes = 
        new java.beans.PropertyChangeSupport(this);

}
