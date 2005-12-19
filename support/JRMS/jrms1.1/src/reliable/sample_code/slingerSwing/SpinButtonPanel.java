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
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
public class SpinButtonPanel extends Panel {        // implements Orientation

    /**
     * This is the Adjustment Event handling innerclass.
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
            Object source;

            source = e.getSource();

            if (source == incBtn) {
                sourceActionEvent("Increment");
            } else if (source == decBtn) {
                sourceActionEvent("Decrement");
            }
        }

    }

    // protected int     orientation;

    protected boolean notifyWhilePressed;
    protected int delay;

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @see
     */
    public SpinButtonPanel() {

        // {{INIT_CONTROLS

        super.setLayout(new GridLayout(2, 1, 0, 0));
        setSize(104, 51);

        incBtn = new DirectionButton();

        try {
            incBtn.setDirection(DirectionButton.UP);
        } catch (java.beans.PropertyVetoException e) {}

        incBtn.setBounds(0, 0, 104, 25);
        add(incBtn);

        decBtn = new DirectionButton();

        try {
            decBtn.setDirection(DirectionButton.DOWN);
        } catch (java.beans.PropertyVetoException e) {}

        decBtn.setBounds(0, 25, 104, 25);
        add(decBtn);

    // }}

    }

    // {{DECLARE_CONTROLS

    DirectionButton incBtn;
    DirectionButton decBtn;

    // }}

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void setOrientation() {
        super.setLayout(new GridLayout(2, 1, 0, 0));
        invalidate();
        validate();
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
        incBtn.setNotifyWhilePressed(f);
        decBtn.setNotifyWhilePressed(f);
    }

    /**
     * Gets the current notifyWhilePressed status.
     * @return true if notify events posted while pressed, false otherwise
     * @see #setNotifyWhilePressed
     * @see #setDelay
     * @see #getDelay
     */
    public boolean isNotifyWhilePressed() {
        return incBtn.isNotifyWhilePressed();
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
        incBtn.setNotifyDelay(d);
        decBtn.setNotifyDelay(d);
    }

    /**
     * Returns the current delay between notification events of the spinner
     * buttons in milliseconds.
     * @see #setNotifyWhilePressed
     * @see #setDelay
     */
    public int getDelay() {
        return incBtn.getNotifyDelay();
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
    public void setLayout(LayoutManager l) {}

    /**
     * Enables or disables this component so that it will respond to user input
     * or not.
     * This is a standard Java AWT method which gets called to enable or disable
     * this component. Once enabled this component will respond to user input.
     * @param flag true if the component is to be enabled,
     * false if it is to be disabled.
     * 
     * @see java.awt.Component#isEnabled
     */
    public synchronized void setEnabled(boolean flag) {
        System.out.println("setEnabled(" + flag + ")");

        if (isEnabled() != flag) {
            if (flag) {
                try {

                    // JDK1.2

                    super.setEnabled(true);
                } catch (NoSuchMethodError e) {

                    // JDK1.1

                    super.enable();
                }

                incBtn.setEnabled(true);
                decBtn.setEnabled(true);
            } else {
                try {

                    // JDK1.2

                    super.setEnabled(false);
                } catch (NoSuchMethodError e) {

                    // JDK1.1

                    super.disable();
                }

                incBtn.setEnabled(false);
                decBtn.setEnabled(false);
            }
        }
    }

    /**
     * This enables or disables the incrementing button only.
     * @param flag true if the incrementing button is to be enabled,
     * false if it is to be disabled.
     * @see #isUpButtonEnabled
     */
    public synchronized void setUpButtonEnabled(boolean flag) {
        if (isUpButtonEnabled() != flag) {
            if (flag) {
                incBtn.setEnabled(true);
            } else {
                incBtn.setEnabled(false);
            }
        }
    }

    /**
     * The enabled state of the incrementing button.
     * @return true if the incrementing button is enabled,
     * false if it is disabled.
     * @see #setUpButtonEnabled
     */
    public boolean isUpButtonEnabled() {
        return incBtn.isEnabled();
    }

    /**
     * This enables or disables the decrementing button only.
     * @param flag true if the decrementing button is to be enabled,
     * false if it is to be disabled.
     * @see #isDownButtonEnabled
     */
    public synchronized void setDownButtonEnabled(boolean flag) {
        if (isDownButtonEnabled() != flag) {
            if (flag) {
                decBtn.setEnabled(true);
            } else {
                decBtn.setEnabled(false);
            }
        }
    }

    /**
     * The enabled state of the decrementing button.
     * @return true if the decrementing button is enabled,
     * false if it is disabled.
     * @see #setDownButtonEnabled
     */
    public boolean isDownButtonEnabled() {
        return decBtn.isEnabled();
    }

    /**
     * @deprecated
     * @see #setUpButtonEnabled
     */
    public synchronized void enableUpButton() {
        setUpButtonEnabled(true);
    }

    /**
     * @deprecated
     * @see #setDownButtonEnabled
     */
    public synchronized void enableDownButton() {
        setDownButtonEnabled(true);
    }

    /**
     * @deprecated
     * @see #setUpButtonEnabled
     */
    public synchronized void disableUpButton() {
        setUpButtonEnabled(false);
    }

    /**
     * @deprecated
     * @see #setDownButtonEnabled
     */
    public synchronized void disableDownButton() {
        setDownButtonEnabled(false);
    }

    /**
     * Tells this component that it has been added to a container.
     * This is a standard Java AWT method which gets called by the AWT when
     * this component is added to a container. Typically, it is used to
     * create this component's peer.
     * 
     * It has been overridden here to hook-up event listeners.
     * 
     * @see #removeNotify
     */
    public synchronized void addNotify() {
        super.addNotify();

        // Hook up listeners

        if (action == null) {
            action = new Action();

            incBtn.addActionListener(action);
            decBtn.addActionListener(action);
        }
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
            incBtn.removeActionListener(action);
            decBtn.removeActionListener(action);

            action = null;
        }

        super.removeNotify();
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
     * Adds the specified action listener to receive action events.
     * The ActionCommand will be either "Increment" or "Decrement"
     * depending on which spinner button was pressed.
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
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Dimension getPreferredSize() {
        int h;
        int w;

        h = 0;
        w = 0;
        w = Math.max(incBtn.getPreferredSize().width, 
                     decBtn.getPreferredSize().width);
        h = incBtn.getPreferredSize().height 
            + decBtn.getPreferredSize().height;

        return (new Dimension(w, h));
    }

    /**
     * Fire an action event to the listeners
     */
    protected void sourceActionEvent(String actionCommand) {
        if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(this, 
                    ActionEvent.ACTION_PERFORMED, actionCommand));
        }
    }

    /**
     * The action listener to keep track of listeners for our action event.
     */
    protected ActionListener actionListener = null;
    private Action action = null;
    private java.beans.VetoableChangeSupport vetos = 
        new java.beans.VetoableChangeSupport(this);
    private java.beans.PropertyChangeSupport changes = 
        new java.beans.PropertyChangeSupport(this);

}
