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

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.Serializable;

// import java.util.ResourceBundle;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
public abstract class Spinner extends Panel implements Serializable {

    /**
     * Undocumented Class Declaration.
     * 
     * 
     * @see
     *
     * @author
     */
    class Action implements ActionListener, java.io.Serializable {

        /**
         * Undocumented Method Declaration.
         * 
         * 
         * @param e
         *
         * @see
         */
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof TextField) {
                if (((TextField) e.getSource()) == textFld) {
                    updateText(false);

                    // Take the focus away from the edit box

                    requestFocus();

                    return;
                }
            }

            String cmdStr = "";
            String actionCommand = e.getActionCommand();

            if (actionCommand.equals("Increment")) {
                scrollUp();

                cmdStr = "ScrollUp";

                sourceActionEvent(cmdStr);
            } else if (actionCommand.equals("Decrement")) {
                scrollDown();

                cmdStr = "ScrollDown";

                sourceActionEvent(cmdStr);
            }
        }

    }

    /**
     * This is the PropertyChangeEvent handling inner class for the 
     * constrained Current property.
     * Handles vetoing Current values that are not valid.
     */
    class CurrentVeto implements VetoableChangeListener, 
        java.io.Serializable {

        /**
         * This method gets called when an attempt to change the constrained 
         * Current property is made.
         * Ensures the given Current value is valid for this component.
         * 
         * @param     e a <code>PropertyChangeEvent</code> object describing the
         * event source and the property that has changed.
         * @exception PropertyVetoException if the recipient wishes the property
         * change to be rolled back.
         */
        public void vetoableChange(PropertyChangeEvent e) 
                throws PropertyVetoException {
            int i = ((Integer) e.getNewValue()).intValue();
        }

    }

    /**
     * This is the PropertyChangeEvent handling inner class for the 
     * constrained Max property.  Handles vetoing Max values that are not valid.
     */
    class MaxVeto implements VetoableChangeListener, Serializable {
        /**
         * This method gets called when an attempt to change the constrained 
         * Current property is made.
         * Ensures the given Max value is valid for this component.
         * 
         * @param     e a <code>PropertyChangeEvent</code> object describing the
         * event source and the property that has changed.
         * @exception PropertyVetoException if the recipient wishes the property
         * change to be rolled back.
         */
        public void vetoableChange(PropertyChangeEvent e) 
            throws PropertyVetoException {

            int i = ((Integer) e.getNewValue()).intValue();
        }

    }

    /**
     * This is the PropertyChangeEvent handling inner class for the 
     * constrained Min property.  Handles vetoing Min values that are not valid.
     */
    class MinVeto implements VetoableChangeListener, java.io.Serializable {
        /**
         * This method gets called when an attempt to change the constrained 
	 * Current property is made.  Ensures the given Min value is valid 
	 * for this component.
         * 
         * @param     e a <code>PropertyChangeEvent</code> object describing the
         * event source and the property that has changed.
         * @exception PropertyVetoException if the recipient wishes the property
         * change to be rolled back.
         */
        public void vetoableChange(PropertyChangeEvent e) 
                throws PropertyVetoException {
            int i = ((Integer) e.getNewValue()).intValue();
        }

    }

    // protected static int ORIENTATION_DEFAULT = ORIENTATION_VERTICAL;

    protected String text;
    protected int textWidth;
    protected int orientation;
    protected boolean wrappable;
    protected boolean editable;
    protected int min;
    protected int max;
    protected int current;
    protected int increment;
    protected VetoableChangeSupport vetos;
    protected PropertyChangeSupport changes;

    // protected ResourceBundle        errors;

    protected ActionListener actionListener;
    protected CurrentVeto currentVeto;
    protected MaxVeto maxVeto;
    protected MinVeto minVeto;
    protected Action action;
    protected boolean added;
    {
        min = 0;
        max = 0;
        increment = 1;
        current = 0;
        textWidth = 0;
        vetos = new VetoableChangeSupport(this);
        changes = new PropertyChangeSupport(this);
        added = false;
    }

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @see
     */
    public Spinner() {

        // {{INIT_CONTROLS

        GridBagLayout gridBagLayout;

        gridBagLayout = new GridBagLayout();

        super.setLayout(gridBagLayout);
        setSize(61, 20);

        textFld = new java.awt.TextField();

        textFld.setBounds(0, 0, 100, 20);

        GridBagConstraints gbc;

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);

        ((GridBagLayout) getLayout()).setConstraints(textFld, gbc);
        add(textFld);

        buttons = new SpinButtonPanel();

        buttons.setLayout(new GridLayout(2, 1, 0, 0));
        buttons.setBounds(100, 0, 3, 20);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.05;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);

        ((GridBagLayout) getLayout()).setConstraints(buttons, gbc);
        add(buttons);

        // }}

        try {
            setWrappable(false);

        // setOrientation(ORIENTATION_VERTICAL);

        } catch (PropertyVetoException e) {}
    }

    // {{DECLARE_CONTROLS

    java.awt.TextField textFld;
    SpinButtonPanel buttons;

    // }}

    /**
     * Conditionally enables editing of the Spinner's TextField.
     * @param f true = allow editing;
     * false = disallow editing
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setEditable(boolean f) throws PropertyVetoException {
        if (editable != f) {
            Boolean oldValue;
            Boolean newValue;

            oldValue = new Boolean(editable);
            newValue = new Boolean(f);

            vetos.fireVetoableChange("editable", oldValue, newValue);

            editable = f;

            textFld.setEditable(editable);
            changes.firePropertyChange("editable", oldValue, newValue);
        }
    }

    /**
     * Returns whether the Spinner's TextField is editable.
     * @return true if the TextField can be edited, false otherwise
     */
    public boolean getEditable() {
        return editable;
    }

    /**
     * Returns whether the Spinner's TextField is editable.
     * @return true if the TextField can be edited, false otherwise
     */
    public boolean isEditable() {
        return editable;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param o
     *
     * @exception PropertyVetoException
     *
     * @see
     */
    public void setOrientation(int o) throws PropertyVetoException {
        if (orientation != o) {
            Integer oldValue;
            Integer newValue;

            oldValue = new Integer(orientation);
            newValue = new Integer(o);

            vetos.fireVetoableChange("orientation", oldValue, newValue);

            orientation = o;

            buttons.setOrientation();
            changes.firePropertyChange("orientation", oldValue, newValue);
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public int getOrientation() {
        return (orientation);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param f
     *
     * @exception PropertyVetoException
     *
     * @see
     */
    public void setWrappable(boolean f) throws PropertyVetoException {
        if (wrappable != f) {
            Boolean oldValue;
            Boolean newValue;

            oldValue = new Boolean(wrappable);
            newValue = new Boolean(f);

            vetos.fireVetoableChange("wrappable", oldValue, newValue);

            wrappable = f;

            updateButtonStatus();
            changes.firePropertyChange("wrappable", oldValue, newValue);
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public boolean getWrappable() {
        return (wrappable);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public boolean isWrappable() {
        return (wrappable);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Dimension getPreferredSize() {
        Dimension textFldDim;
        Dimension btnsDim;

        textFldDim = textFld.getPreferredSize();
        btnsDim = buttons.getPreferredSize();

        return (new Dimension(textFldDim.width + btnsDim.width, 
                              Math.max(textFldDim.height, btnsDim.height)));
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Dimension getMinimumSize() {
        return (getPreferredSize());
    }

    /**
     * Sets the minimum value the spinner may have.
     * @param i the new minimum value
     * @see #getMin
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setMin(int i) throws PropertyVetoException {
        if (min != i) {
            Integer oldValue;
            Integer newValue;

            oldValue = new Integer(min);
            newValue = new Integer(i);

            vetos.fireVetoableChange("min", oldValue, newValue);

            min = i;

            if (getCurrent() < min) {
                setCurrent(min);
            } else {
                updateButtonStatus();
            }

            changes.firePropertyChange("min", oldValue, newValue);
        }
    }

    /**
     * Gets the current minimum value the spinner may have.
     * @return the current minimum value
     * @see #setMin
     */
    public int getMin() {
        return (min);
    }

    /**
     * Sets the maximum value the spinner may have.
     * @param i the new maximum value
     * @see #getMax
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setMax(int i) throws PropertyVetoException {
        if (max != i) {
            Integer oldValue;
            Integer newValue;

            oldValue = new Integer(max);
            newValue = new Integer(i);

            vetos.fireVetoableChange("max", oldValue, newValue);

            max = i;

            if (getCurrent() > max) {
                setCurrent(max);
            } else {
                updateButtonStatus();
            }

            changes.firePropertyChange("max", oldValue, newValue);
        }
    }

    /**
     * Gets the current maximum value the spinner may have.
     * @return the current maximum value
     * @see #setMax
     */
    public int getMax() {
        return (max);
    }

    /**
     * Sets the value of the spinner.
     * @param i the new value
     * @see #getCurrent
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setCurrent(int i) throws PropertyVetoException {
        if (current != i) {
            Integer oldValue;
            Integer newValue;

            oldValue = new Integer(current);
            newValue = new Integer(i);

            vetos.fireVetoableChange("current", oldValue, newValue);

            current = i;

            updateText(false);
            updateButtonStatus();
            changes.firePropertyChange("current", oldValue, newValue);
        }
    }

    /**
     * Gets the current value of the spinner.
     * @return the current spinner value
     * @see #setCurrent
     */
    public int getCurrent() {
        return (current);
    }

    /**
     * Sets whether the spinner buttons will continually post notify events
     * while pressed.
     * @param f true = send messages; false = do not send messages
     * @see #isNotifyWhilePressed
     * @see #setDelay
     * @see #getDelay
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setNotifyWhilePressed(boolean f) 
            throws PropertyVetoException {
        if (f != buttons.getNotifyWhilePressed()) {
            Boolean oldValue;
            Boolean newValue;

            oldValue = new Boolean(getNotifyWhilePressed());
            newValue = new Boolean(f);

            vetos.fireVetoableChange("notifyWhilePressed", oldValue, 
                                     newValue);
            buttons.setNotifyWhilePressed(f);
            changes.firePropertyChange("notifyWhilePressed", oldValue, 
                                       newValue);
        }
    }

    /**
     * Gets the current notifyWhilePressed status.
     * @return true if notify events posted while pressed, false otherwise
     * @see #setNotifyWhilePressed
     * @see #setDelay
     * @see #getDelay
     */
    public boolean isNotifyWhilePressed() {
        return (buttons.isNotifyWhilePressed());
    }

    /**
     * @deprecated
     * @see #isNotifyWhilePressed
     */
    public boolean getNotifyWhilePressed() {
        return isNotifyWhilePressed();
    }

    /**
     * Sets the notification event delay of the spinner buttons in milliseconds.
     * @param d the delay between notification events in milliseconds
     * @see #setNotifyWhilePressed
     * @see #getDelay
     * 
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setDelay(int d) throws PropertyVetoException {
        if (d != buttons.getDelay()) {
            Integer oldValue;
            Integer newValue;

            oldValue = new Integer(buttons.getDelay());
            newValue = new Integer(d);

            vetos.fireVetoableChange("delay", oldValue, newValue);
            buttons.setDelay(d);
            changes.firePropertyChange("delay", oldValue, newValue);
        }
    }

    /**
     * Returns the current delay between notification events of the spinner
     * buttons in milliseconds.
     * @see #setNotifyWhilePressed
     * @see #setDelay
     */
    public int getDelay() {
        return buttons.getDelay();
    }

    /**
     * Returns the text that is in the entry TextField.
     */
    public String getEntryFieldText() {
        return (textFld.getText());
    }

    /**
     * Takes no action.
     * This is a standard Java AWT method which gets called to specify
     * which layout manager should be used to layout the components in
     * standard containers.
     * 
     * Since layout managers CANNOT BE USED with this container the standard
     * setLayout has been OVERRIDDEN for this container and does nothing.
     * 
     * @param l the layout manager to use to layout this container's components
     * (IGNORED)
     * @see java.awt.Container#getLayout
     */
    public void setLayout(LayoutManager lm) {}

    /**
     * Tells this component that it has been added to a container.
     * This is a standard Java AWT method which gets called by the AWT when
     * this component is added to a container. Typically, it is used to
     * create this component's peer.
     * 
     * It has been overridden here to hook-up event listeners.
     * It is also used to setup the component, creating the TextField as needed.
     * 
     * @see #removeNotify
     */
    public synchronized void addNotify() {
        super.addNotify();

        added = true;

        // errors = ResourceBundle.getBundle("SlingerResources");
        // Hook up listeners

        if (action == null) {
            action = new Action();

            buttons.addActionListener(action);
            textFld.addActionListener(action);
        }
        if (currentVeto == null) {
            currentVeto = new CurrentVeto();

            addCurrentListener(currentVeto);
        }
        if (maxVeto == null) {
            maxVeto = new MaxVeto();

            addMaxListener(maxVeto);
        }
        if (minVeto == null) {
            minVeto = new MinVeto();

            addMinListener(minVeto);
        }

        updateText(true);
    }

    /**
     * Tells this component that it is being removed from a container.
     * This is a standard Java AWT method which gets called by the AWT when
     * this component is removed from a container. Typically, it is used to
     * destroy the peers of this component and all its subcomponents.
     * 
     * It has been overridden here to unhook event listeners.
     * 
     * @see #addNotify
     */
    public synchronized void removeNotify() {

        // Unhook listeners

        if (action != null) {
            textFld.removeActionListener(action);
            buttons.removeActionListener(action);

            action = null;
        }
        if (currentVeto != null) {
            removeCurrentListener(currentVeto);

            currentVeto = null;
        }
        if (maxVeto != null) {
            removeMaxListener(maxVeto);

            maxVeto = null;
        }
        if (minVeto != null) {
            removeMinListener(minVeto);

            minVeto = null;
        }

        super.removeNotify();
    }

    /**
     * Adds the specified action listener to receive action events
     * from this button.
     * @param l the action listener
     */
    public synchronized void addActionListener(ActionListener l) {
        actionListener = AWTEventMulticaster.add(actionListener, l);
    }

    /**
     * Removes the specified action listener so it no longer receives
     * action events from this component.
     * @param l the action listener
     */
    public synchronized void removeActionListener(ActionListener l) {
        actionListener = AWTEventMulticaster.remove(actionListener, l);
    }

    /**
     * Adds a listener for all property change events.
     * @param listener the listener to add
     * @see #removePropertyChangeListener
     */
    public synchronized void addPropertyChangeListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for all property change events.
     * @param listener the listener to remove
     * @see #addPropertyChangeListener
     */
    public synchronized void removePropertyChangeListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a listener for all vetoable property change events.
     * @param listener the listener to add
     * @see #removeVetoableChangeListener
     */
    public synchronized void addVetoableChangeListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a listener for all vetoable property change events.
     * @param listener the listener to remove
     * @see #addVetoableChangeListener
     */
    public synchronized void removeVetoableChangeListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a listener for the current property changes.
     * @param listener the listener to add.
     * @see #removeCurrentListener(java.beans.PropertyChangeListener)
     */
    public synchronized void addCurrentListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for the current property changes.
     * @param listener the listener to remove.
     * @see #addCurrentListener(java.beans.PropertyChangeListener)
     */
    public synchronized void removeCurrentListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for the current property changes.
     * @param listener the listener to add.
     * @see #removeCurrentListener(java.beans.VetoableChangeListener)
     */
    public synchronized void addCurrentListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for the current property changes.
     * @param listener the listener to remove.
     * @see #addCurrentListener(java.beans.VetoableChangeListener)
     */
    public synchronized void removeCurrentListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a listener for the max property changes.
     * @param listener the listener to add.
     * @see #removeMaxListener(java.beans.PropertyChangeListener)
     */
    public synchronized void addMaxListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for the max property changes.
     * @param listener the listener to remove.
     * @see #addMaxListener(java.beans.PropertyChangeListener)
     */
    public synchronized void removeMaxListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for the max property changes.
     * @param listener the listener to add.
     * @see #removeMaxListener(java.beans.VetoableChangeListener)
     */
    public synchronized void addMaxListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for the max property changes.
     * @param listener the listener to remove.
     * @see #addMaxListener(java.beans.VetoableChangeListener)
     */
    public synchronized void removeMaxListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a listener for the min property changes.
     * @param listener the listener to add.
     * @see #removeMinListener(java.beans.PropertyChangeListener)
     */
    public synchronized void addMinListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for the min property changes.
     * @param listener the listener to remove.
     * @see #addMinListener(java.beans.PropertyChangeListener)
     */
    public synchronized void removeMinListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for the min property changes.
     * @param listener the listener to add.
     * @see #removeMinListener(java.beans.VetoableChangeListener)
     */
    public synchronized void addMinListener(VetoableChangeListener listener) {
        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for the min property changes.
     * @param listener the listener to remove.
     * @see #addMinListener(java.beans.VetoableChangeListener)
     */
    public synchronized void removeMinListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Is the given value valid for the Current property .
     * @param i the given value
     * @return true if the given value is acceptable, false if not.
     */
    protected boolean isValidCurrentValue(int i) {
        if (i > max || i < min) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Is the given value valid for the Max property .
     * @param i the given value
     * @return true if the given value is acceptable, false if not.
     */
    protected boolean isValidMaxValue(int i) {
        return (i >= min);
    }

    /**
     * Is the given value valid for the Min property .
     * @param i the given value
     * @return true if the given value is acceptable, false if not.
     */
    protected boolean isValidMinValue(int i) {
        return (i <= max);
    }

    /**
     * Fire an action event to the listeners
     */
    protected void sourceActionEvent(String s) {
        if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(this, 
                    ActionEvent.ACTION_PERFORMED, s));
        } 
    }

    /**
     * Increments the spinner's value and handles wrapping as needed.
     * @see #scrollDown
     * @see #increment
     */
    protected void scrollUp() {
        try {
            setCurrent(current + increment);
        } catch (PropertyVetoException exc) {
            if (wrappable) {
                try {
                    setCurrent(min);
                } catch (PropertyVetoException ex) {}
            } else {
                try {
                    setCurrent(max);
                } catch (PropertyVetoException ex) {}
            }
        }

        updateText(false);
    }

    /**
     * Decrements the spinner's value and handles wrapping as needed.
     * @see #scrollUp
     * @see #increment
     */
    protected void scrollDown() {
        try {
            setCurrent(current - increment);
        } catch (PropertyVetoException exc) {
            if (wrappable) {
                try {
                    setCurrent(max);
                } catch (PropertyVetoException exc1) {}
            } else {
                try {
                    setCurrent(min);
                } catch (PropertyVetoException exc1) {}
            }
        }

        updateText(false);
    }

    /**
     * Updates the text field with the current text, as needed or depending 
     * on the force flag.
     * @param force If true, causes the text field to update even if the 
     * value has not changed.
     * @see #updateText()
     * @see #getCurrentText
     */
    protected void updateText(boolean force) {
        String currentText;

        currentText = getCurrentText();

        // If the text has changed, put the new text into the text field

        if (force ||!textFld.getText().equals(currentText)) {
            textFld.setText(currentText);
        }
    }

    /**
     * Handles enabling or disabling the spinner buttons as needed.
     */
    protected void updateButtonStatus() {
        if (buttons != null) {
            if (wrappable) {
                buttons.setUpButtonEnabled(true);
                buttons.setDownButtonEnabled(true);
            } else {
                if (current == max && current == min) {
                    buttons.setUpButtonEnabled(false);
                    buttons.setDownButtonEnabled(false);
                } else if (current == max) {
                    buttons.setUpButtonEnabled(false);
                    buttons.setDownButtonEnabled(true);
                } else if (current == min) {
                    buttons.setUpButtonEnabled(true);
                    buttons.setDownButtonEnabled(false);
                } else {
                    buttons.setUpButtonEnabled(true);
                    buttons.setDownButtonEnabled(true);
                }
            }
        }
    }

    /**
     * Gets the currently selected string from the list.
     * @return the string currently visible in the Spinner
     * @see #updateText
     */
    protected abstract String getCurrentText();

}
