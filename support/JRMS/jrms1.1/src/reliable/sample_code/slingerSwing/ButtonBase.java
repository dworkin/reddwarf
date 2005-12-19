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
import java.applet.*;
import java.awt.event.KeyEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.beans.PropertyVetoException;
import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeEvent;
import java.lang.IllegalArgumentException;

// import java.util.ResourceBundle;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
public abstract class ButtonBase extends Canvas {
    ColorUtils colorUtils;

    /**
     * Constructs a default ButtonBase. The defaults are no notifyWhilePressed,
     * no offset, and a bevel height of 2.
     */
    protected ButtonBase() {
        colorUtils = new ColorUtils();
        pressed = false;
        released = true;
        notifyWhilePressed = false;
        running = false;
        notified = false;
        useOffset = false;
        showURLStatus = true;
        isAdded = false;

        // notifyTimer       = null;

        notifyDelay = 1000;
        bevel = 2;
        pressedAdjustment = 0;
        tempBevelHeight = bevel;

        try {
            setBorderColor(Color.black);
            setButtonColor(Color.lightGray);
        } catch (PropertyVetoException exc) {}
        try {
            setShowFocus(true);
        } catch (PropertyVetoException e) {}
    }

    /**
     * Sets the "height" (cross-section) of a beveled edge, in pixels.
     * @param height the size of the bevel in pixels
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #getBevelHeight
     */
    public void setBevelHeight(int height) throws PropertyVetoException {
        if (isAdded) {
            if (bevel != height) {
                Integer oldValue = new Integer(bevel);
                Integer newValue = new Integer(height);

                vetos.fireVetoableChange("bevelHeight", oldValue, newValue);

                bevel = height;
                tempBevelHeight = height;

                repaint();
                changes.firePropertyChange("bevelHeight", oldValue, newValue);
            }
        } else {

            // We store the value until we are added then set 
	    // the value to avoid code-gen order dependencies.

            tempBevelHeight = height;
        }
    }

    /**
     * Returns the current "height" (cross-section) of a beveled edge, 
     * in pixels.
     * @return the current bevel height in pixels.
     * @see #setBevelHeight
     */
    public int getBevelHeight() {
        return isAdded ? bevel : tempBevelHeight;
    }

    /**
     * Sets whether the button will continually post notify events 
     * while pressed.
     * @param flag true to post notify events; false to not post events
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #isNotifyWhilePressed
     * @see #setNotifyDelay
     * @see #getNotifyDelay
     */
    public void setNotifyWhilePressed(boolean flag) 
        throws PropertyVetoException {

        if (notifyWhilePressed != flag) {
            Boolean oldValue = new Boolean(notifyWhilePressed);
            Boolean newValue = new Boolean(flag);

            vetos.fireVetoableChange("notifyWhilePressed", oldValue, 
                                     newValue);

            notifyWhilePressed = flag;

            if (notifyWhilePressed) {

            // notifyTimer = new Timer(notifyDelay, true);
            // notifyTimer.addActionListener(action);
            // } else if (notifyTimer != null) {
            // notifyTimer = null;

            }

            changes.firePropertyChange("notifyWhilePressed", oldValue, 
                                       newValue);
        }
    }

    /**
     * Gets whether the button will continuously post events while pressed.
     * @return true if it will continuously post events while pressed, false
     * otherwise
     * @see #setNotifyWhilePressed
     * @see #setNotifyDelay
     * @see #getNotifyDelay
     */
    public boolean isNotifyWhilePressed() {
        return notifyWhilePressed;
    }

    /**
     * @deprecated
     * @see #isNotifyWhilePressed
     */
    public boolean getNotifyWhilePressed() {
        return isNotifyWhilePressed();
    }

    /**
     * Sets the notification event delay in milliseconds.
     * @param delay the delay between notification events in milliseconds
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #setNotifyWhilePressed
     * @see #getNotifyDelay
     */
    public void setNotifyDelay(int delay) throws PropertyVetoException {
        if (notifyDelay != delay) {
            Integer oldValue = new Integer(notifyDelay);
            Integer newValue = new Integer(delay);

            vetos.fireVetoableChange("notifyDelay", oldValue, newValue);

            notifyDelay = delay;

            // if (notifyTimer != null)
            // notifyTimer.setDelay(notifyDelay);

            changes.firePropertyChange("notifyDelay", oldValue, newValue);
        }
    }

    /**
     * Returns the current delay in milliseconds between notification events.
     * @see #setNotifyWhilePressed
     * @see #setNotifyDelay
     */
    public int getNotifyDelay() {
        return notifyDelay;
    }

    /**
     * Sets whether objects in the button will be offset down and to the right
     * bevel height amount or not.
     * This also impacts the way the button is drawn when it is pressed.
     * @param flag true to have objects use the offset; false to have 
     * objects not move.
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #isUseOffset
     * @see #setBevelHeight
     * @see #getBevelHeight
     */
    public void setUseOffset(boolean flag) throws PropertyVetoException {
        if (useOffset != flag) {
            Boolean oldValue = new Boolean(useOffset);
            Boolean newValue = new Boolean(flag);

            vetos.fireVetoableChange("useOffset", oldValue, newValue);

            useOffset = flag;

            repaint();
            changes.firePropertyChange("useOffset", oldValue, newValue);
        }
    }

    /**
     * Returns whether this button will be shown as having the focus when 
     * the mouse enters.
     * @see #setShowFocus
     */
    public boolean isUseOffset() {
        return useOffset;
    }

    /**
     * Sets whether this button will be shown as having the focus 
     * when the mouse enters.
     * @param flag true to show focus at mouse enter; false to not 
     * show at mouse enter
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #isShowFocus
     */
    public void setShowFocus(boolean flag) throws PropertyVetoException {
        if (showFocus != flag) {
            Boolean oldValue = new Boolean(showFocus);
            Boolean newValue = new Boolean(flag);

            vetos.fireVetoableChange("showFocus", oldValue, newValue);

            showFocus = flag;

            changes.firePropertyChange("showFocus", oldValue, newValue);
        }
    }

    /**
     * Returns whether this button will be shown as having the focus 
     * when the mouse enters.
     * @see #setShowFocus
     */
    public boolean isShowFocus() {
        return showFocus;
    }

    /**
     * @deprecated
     * @see #isShowFocus
     */
    public boolean getShowFocus() {
        return isShowFocus();
    }

    /**
     * Sets whether the linkURL will be displayed in the status area 
     * when the mouse is over the button.
     * This flag also controls erasing of the status area after the URL 
     * has been displayed.
     * @param flag true if the linkURL will be displayed in the status 
     * area when the mouse is over the button; false if not.
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #isShowURLStatus
     */
    public void setShowURLStatus(boolean flag) throws PropertyVetoException {
        if (showURLStatus != flag) {
            Boolean oldValue = new Boolean(showURLStatus);
            Boolean newValue = new Boolean(flag);

            vetos.fireVetoableChange("showURLStatus", oldValue, newValue);

            showURLStatus = flag;

            changes.firePropertyChange("showURLStatus", oldValue, newValue);
        }
    }

    /**
     * If true show the linkURL in the status area when the mouse is over 
     * the button.  If the linkURL is null, nothing is displayed, 
     * regardless of this flag.  This flag also controls erasing of the 
     * status area after the URL has been displayed.
     * @return true if the linkURL will be displayed in the status area 
     * when the mouse is over the button.
     * @see #setShowURLStatus
     */
    public boolean isShowURLStatus() {
        return showURLStatus;
    }

    /**
     * Sets the current border color.
     * @param color the new border color
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #getBorderColor
     */
    public void setBorderColor(Color color) throws PropertyVetoException {

        // if (!GeneralUtils.objectsEqual(borderColor, color)) {

        Color oldValue = borderColor;

        vetos.fireVetoableChange("borderColor", oldValue, color);

        borderColor = color;

        try {
            disabledBorderColor = colorUtils.lighten(borderColor, 0.466);
        } catch (IllegalArgumentException exc) {}

        repaint();
        changes.firePropertyChange("borderColor", oldValue, color);

        // }

    }

    /**
     * Gets the current border color.
     * @return the current border color
     * @see #setBorderColor
     */
    public Color getBorderColor() {
        return borderColor;
    }

    /**
     * Sets the current button color.
     * @param color the new button color.  The highlights of the button are
     * derived from this color.
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #getButtonColor
     */
    public void setButtonColor(Color color) throws PropertyVetoException {

        // if (!GeneralUtils.objectsEqual(buttonColor, color)) {

        Color oldValue = buttonColor;

        vetos.fireVetoableChange("buttonColor", oldValue, color);

        buttonColor = color;

        try {
            hilightColor = colorUtils.lighten(buttonColor, 0.600);
            pressedHilightColor = colorUtils.darken(buttonColor, 0.580);
            disabledHilightColor = colorUtils.lighten(buttonColor, 0.666);
            shadowColor = colorUtils.darken(buttonColor, 0.250);
            pressedShadowColor = colorUtils.darken(buttonColor, 0.100);
            disabledShadowColor = colorUtils.darken(buttonColor, 0.166);
            disabledButtonColor = colorUtils.lighten(buttonColor, 0.333);
            pressedButtonColor = colorUtils.darken(buttonColor, 0.250);
        } catch (IllegalArgumentException exc) {}

        repaint();
        changes.firePropertyChange("buttonColor", oldValue, color);

    // }

    }

    /**
     * Gets the current button color.
     * @return the current button color
     * @see #setButtonColor
     */
    public Color getButtonColor() {
        return buttonColor;
    }

    /**
     * Sets the URL of the document to link to when the button is clicked.
     * @param url the URL
     * @exception java.beans.PropertyVetoException
     * if the specified property value is unacceptable
     * @see #getLinkURL
     */
    public void setLinkURL(URL url) throws PropertyVetoException {

        // if (!GeneralUtils.objectsEqual(linkURL, url)) {

        URL oldValue = linkURL;

        vetos.fireVetoableChange("linkURL", oldValue, url);

        linkURL = url;
        context = null;

        changes.firePropertyChange("linkURL", oldValue, url);

    // }

    }

    /**
     * Returns the URL of the document to link to when the button is clicked.
     * @see #setLinkURL
     */
    public URL getLinkURL() {
        return linkURL;
    }

    /**
     * Sets the frame specifier for showing a URL document in a browser 
     * or applet viewer. It is interpreted as follows:
     * <UL>
     * <DT>"_self"  show document in the current frame</DT>
     * <DT>"_parent"    show document in the parent frame</DT>
     * <DT>"_top"   show document in the topmost frame</DT>
     * <DT>"_blank" show document in a new unnamed toplevel window</DT>
     * <DT>all others   show document in a new toplevel window with the 
     * given name</DT>
     * </UL>
     * @param newFrame the frame specifier
     * @exception java.beans.PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setFrame(String newFrame) throws PropertyVetoException {
        String oldValue = frame;

        vetos.fireVetoableChange("frame", oldValue, newFrame);

        frame = newFrame;

        changes.firePropertyChange("frame", oldValue, newFrame);
    }

    /**
     * Gets the frame specifier for showing a URL document in a browser or 
     * applet viewer. It is interpreted as follows:
     * <UL>
     * <DT>"_self"  show document in the current frame</DT>
     * <DT>"_parent"    show document in the parent frame</DT>
     * <DT>"_top"   show document in the topmost frame</DT>
     * <DT>"_blank" show document in a new unnamed toplevel window</DT>
     * <DT>all others   show document in a new toplevel window with 
     * the given name</DT>
     * </UL>
     * @return the frame specifier
     * @see #setFrame
     */
    public String getFrame() {
        return frame;
    }

    /**
     * Ensures that this component is laid out properly, as needed.
     * This is a standard Java AWT method which gets called by the AWT to
     * make sure this component and its subcomponents have a valid layout.
     * If this component was made invalid with a call to invalidate(), then
     * it is laid out again.
     * 
     * It is overridden here to locate the applet containing this component.
     * 
     * @see java.awt.Component#invalidate
     */
    public void validate() {

        // On validation, try to find the containing applet.  If we can find
        // it, we don't bother doing the link...

        if (context == null) {
            Container c;

            c = getParent();

            while (c != null) {
                if (c instanceof Applet) {
                    setAppletContext(((Applet) c).getAppletContext());

                    break;
                }

                c = c.getParent();
            }
        }
    }

    /**
     * Enables this component so that it will respond to user input.
     * This is a standard Java AWT method which gets called to enable or disable
     * this component. Once enabled this component will respond to user input.
     * 
     * @param flag true if the component is to be enabled; false if it 
     * is to be disabled.
     * @see java.awt.Component#isEnabled
     */
    public void setEnabled(boolean flag) {

        // System.out.println("Component::setEnabled(" + flag + ")");

        if (isEnabled() != flag) {
            if (flag) {

                // !!! LAB !!!    This MUST be a call to super.enable(),
                // not super.enable(boolean) or super.setEnabled(boolean).
                // If it is not, then it will result in an endless loop!
                // 
                // JEP Not sure the comment above is correct.
                // 

                try {

                    // JDK 1.2

                    super.setEnabled(true);
                } catch (NoSuchMethodError e) {

                    // JDK 1.1

                    super.enable();
                }
            } else {

                // !!! LAB !!!    This MUST be a call to super.disable(),
                // not super.enable(boolean) or super.setEnabled(boolean).
                // If it is not, then it will result in an endless loop!
                // 
                // JEP Not sure the comment above is correct.
                // 

                try {

                    // JDK 1.2

                    super.setEnabled(false);
                } catch (NoSuchMethodError e) {

                    // JDK 1.1

                    super.disable();
                }
            }

            pressed = false;
            pressedAdjustment = 0;

            repaint();
        }
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

        // errors = ResourceBundle.getBundle("SlingerResources");
        // Hook up listeners

        if (focus == null) {
            focus = new Focus();

            addFocusListener(focus);
        }
        if (key == null) {
            key = new Key();

            addKeyListener(key);
        }
        if (mouse == null) {
            mouse = new Mouse();

            addMouseListener(mouse);
        }
        if (bevelVeto == null) {
            bevelVeto = new BevelVeto();

            addBevelHeightListener(bevelVeto);
        }
        if (frameVeto == null) {
            frameVeto = new FrameVeto();

            addFrameListener(frameVeto);
        }

        // Add after the listeners are hooked up

        super.addNotify();

        isAdded = true;

        verifyContstrainedPropertyValues();

        // On addNotify, try to find the containing applet.

        if (context == null) {
            Container c;

            c = getParent();

            while (c != null) {
                if (c instanceof Applet) {
                    setAppletContext(((Applet) c).getAppletContext());

                    break;
                }

                c = c.getParent();
            }
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

        if (focus != null) {
            removeFocusListener(focus);

            focus = null;
        }
        if (key != null) {
            removeKeyListener(key);

            key = null;
        }
        if (mouse != null) {
            removeMouseListener(mouse);

            mouse = null;
        }
        if (bevelVeto != null) {
            removeBevelHeightListener(bevelVeto);

            bevelVeto = null;
        }
        if (frameVeto != null) {
            removeFrameListener(frameVeto);

            frameVeto = null;
        }

        super.removeNotify();

        isAdded = false;
    }

    /**
     * Handles redrawing of this component on the screen.
     * This is a standard Java AWT method which gets called by the Java
     * AWT (repaint()) to handle repainting this component on the screen.
     * The graphics context clipping region is set to the bounding rectangle
     * of this component and its [0,0] coordinate is this component's
     * top-left corner.
     * Typically this method paints the background color to clear the
     * component's drawing space, sets graphics context to be the foreground
     * color, and then calls paint() to draw the component.
     * 
     * It is overridden here to prevent the flicker associated with the standard
     * update() method's repainting of the background before painting 
     * the component itself.
     * 
     * @param g the graphics context
     * @see java.awt.Component#repaint
     * @see #paint
     */
    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Paints this component using the given graphics context.
     * This is a standard Java AWT method which typically gets called
     * by the AWT to handle painting this component. It paints this component
     * using the given graphics context. The graphics context clipping region
     * is set to the bounding rectangle of this component and its [0,0]
     * coordinate is this component's top-left corner.
     * 
     * @param g the graphics context used for painting
     * @see java.awt.Component#repaint
     * @see #update
     */
    public void paint(Graphics g) {
        updateButtonImage();
        g.drawImage(buttonImage, 0, 0, this);
    }

    /**
     * Returns the recommended dimensions to properly display this component.
     * This is a standard Java AWT method which gets called to determine
     * the recommended size of this component.
     */
    public Dimension getPreferredSize() {
        return new Dimension(bevel + bevel + 2, bevel + bevel + 2);
    }

    /**
     * @deprecated
     * @see #getPreferredSize
     */
    public Dimension preferredSize() {
        return getPreferredSize();
    }

    /**
     * Returns the minimum dimensions to properly display this component.
     * This is a standard Java AWT method which gets called to determine
     * the minimum size of this component.
     * It simply returns the results of a call to preferedSize().
     */
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    /**
     * @deprecated
     * @see #getMinimumSize
     */
    public Dimension minimumSize() {
        return getMinimumSize();
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public boolean isFocusTraversable() {
        return true;
    }

    /**
     * Makes the button act as if it was pressed, drawing
     * as if it were clicked and sending an action event as if it
     * were clicked.
     */
    public void simulateClick() {

        // pressed

        requestFocus();

        inButton = true;
        pressed = true;
        released = false;

        if (useOffset) {
            pressedAdjustment = bevel;
        } 

        paint(getGraphics());

        // Wait for a bit

        try {
            Thread.sleep(120);
        } catch (java.lang.InterruptedException exc) {}

        // released

        inButton = false;
        pressed = false;
        pressedAdjustment = 0;

        linkToURL();
        sourceActionEvent();

        released = true;

        repaint();
    }

    /**
     * Adds a listener for all event changes.
     * @param listener the listener to add.
     * @see #removePropertyChangeListener
     */
    public synchronized void addPropertyChangeListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for all event changes.
     * @param listener the listener to remove.
     * @see #addPropertyChangeListener
     */
    public synchronized void removePropertyChangeListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for all event changes.
     * @param listener the listener to add.
     * @see #removeVetoableChangeListener
     */
    public synchronized void addVetoableChangeListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for all event changes.
     * @param listener the listener to remove.
     * @see #addVetoableChangeListener
     */
    public synchronized void removeVetoableChangeListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a listener for the BevelHeight property changes.
     * @param listener the listener to add.
     * @see #removeBevelHeightListener(java.beans.PropertyChangeListener)
     */
    public synchronized void addBevelHeightListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for the BevelHeight property changes.
     * @param listener the listener to remove.
     * @see #addBevelHeightListener(java.beans.PropertyChangeListener)
     */
    public synchronized void removeBevelHeightListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for the BevelHeight property changes.
     * @param listener the listener to add.
     * @see #removeBevelHeightListener(java.beans.VetoableChangeListener)
     */
    public synchronized void addBevelHeightListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for the BevelHeight property changes.
     * @param listener the listener to remove.
     * @see #addBevelHeightListener(java.beans.VetoableChangeListener)
     */
    public synchronized void removeBevelHeightListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a listener for the Frame property changes.
     * @param listener the listener to add.
     * @see #removeFrameListener(java.beans.PropertyChangeListener)
     */
    public synchronized void addFrameListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for the Frame property changes.
     * @param listener the listener to remove.
     * @see #addFrameListener(java.beans.PropertyChangeListener)
     */
    public synchronized void removeFrameListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for the Frame property changes.
     * @param listener the listener to add.
     * @see #removeFrameListener(java.beans.VetoableChangeListener)
     */
    public synchronized void addFrameListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for the Frame property changes.
     * @param listener the listener to remove.
     * @see #addFrameListener(java.beans.VetoableChangeListener)
     */
    public synchronized void removeFrameListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Sets the command name of the action event fired by this button.
     * @param command The name of the action event command fired by this button
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     */
    public void setActionCommand(String command) 
        throws PropertyVetoException {

        String oldValue = actionCommand;

        vetos.fireVetoableChange("actionCommand", oldValue, command);

        actionCommand = command;

        changes.firePropertyChange("actionCommand", oldValue, command);
    }

    /**
     * Returns the command name of the action event fired by this button.
     * @return the action command name
     */
    public String getActionCommand() {
        return actionCommand;
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
     * action events from this button.
     * @param l the action listener
     */
    public synchronized void removeActionListener(ActionListener l) {
        actionListener = AWTEventMulticaster.remove(actionListener, l);
    }

    /**
     * Undocumented Class Declaration.
     * 
     * 
     * @see
     *
     * @author
     */
    class Focus extends java.awt.event.FocusAdapter implements 
    java.io.Serializable {

        /**
         * Undocumented Method Declaration.
         * 
         * 
         * @param e
         *
         * @see
         */
        public void focusGained(FocusEvent e) {
            hasFocus = true;

            repaint();
        }

        /**
         * Undocumented Method Declaration.
         * 
         * 
         * @param e
         *
         * @see
         */
        public void focusLost(FocusEvent e) {
            hasFocus = false;

            repaint();
        }

    }

    /**
     * Undocumented Class Declaration.
     * 
     * 
     * @see
     *
     * @author
     */
    class Key extends java.awt.event.KeyAdapter implements 
    java.io.Serializable {

        /**
         * Undocumented Method Declaration.
         * 
         * 
         * @param evt
         *
         * @see
         */
        public void keyPressed(KeyEvent evt) {
            boolean isSpaceBar = (evt.getKeyCode() & KeyEvent.VK_SPACE) == 
		KeyEvent.VK_SPACE;

            if (isSpaceBar && hasFocus && showFocus) {
                inButton = true;
                notified = false;

                // if (notifyTimer != null && notifyWhilePressed && !running) {
                // running     = true;
                // notifyTimer.start();
                // }

                pressed = true;
                released = false;

                if (useOffset) {
                    pressedAdjustment = bevel;
                } 

                repaint();
            }
        }

        /**
         * Undocumented Method Declaration.
         * 
         * 
         * @param evt
         *
         * @see
         */
        public void keyReleased(KeyEvent evt) {
            boolean isSpaceBar = (evt.getKeyCode() & KeyEvent.VK_SPACE) == 
		KeyEvent.VK_SPACE;

            if (isSpaceBar && hasFocus && showFocus) {
                inButton = false;

                if (pressed) {
                    pressed = false;
                    pressedAdjustment = 0;

                    if (!notifyWhilePressed ||!notified) {

                        // Handle going to the linkURL

                        linkToURL();
                        sourceActionEvent();
                    }
                }

                released = true;

                repaint();
            }
        }

    }

    /**
     * This is the Mouse Event handling innerclass.
     */
    class Mouse extends java.awt.event.MouseAdapter implements 
	java.io.Serializable {

        /**
         * Handles the Mouse Pressed events
         * If the notifyWhilePressed flag is true the notification 
	 * Timer is started
         * @param e the MouseEvent
         * @see #setNotifyWhilePressed
         * @see #setNotifyDelay
         * @see #mouseReleased
         */
        public void mousePressed(MouseEvent e) {
            requestFocus();

            notified = false;

            // if (notifyTimer != null && notifyWhilePressed && !running) {
            // running     = true;
            // notifyTimer.start();
            // }

            pressed = true;
            released = false;

            if (useOffset) {
                pressedAdjustment = bevel;
            } 

            repaint();
        }

        /**
         * Handles the Mouse Released events
         * If the notification timer is running it is stopped.
         * If the mouse was pressed inside the button then fire an action event.
         * @param e the MouseEvent
         * @see #mousePressed
         */
        public void mouseReleased(MouseEvent e) {
            if (pressed) {
                pressed = false;
                pressedAdjustment = 0;

                if (!notifyWhilePressed ||!notified) {

                    // Handle going to the linkURL

                    linkToURL();
                    sourceActionEvent();
                }
            }

            released = true;

            if (inButton) {
                repaint();
            } 
        }

        /**
         * Handles Mouse Entered events
         * @param e the MouseEvent
         */
        public void mouseEntered(MouseEvent e) {
            inButton = true;

            // Display the linkURL

            if (showURLStatus && context != null && linkURL != null) {
                context.showStatus(linkURL.toString());
            }
            if (!released) {
                pressed = true;

                if (useOffset) {
                    pressedAdjustment = bevel;
                } 

                // if (notifyTimer != null && notifyWhilePressed && !running) {
                // running = true;
                // notifyTimer.start();
                // }

                repaint();
            }
        }

        /**
         * Handles Mouse Exited events
         * @param e the MouseEvent
         */
        public void mouseExited(MouseEvent e) {
            inButton = false;

            if (pressed) {
                pressed = false;
                pressedAdjustment = 0;

                repaint();
            }
            if (showURLStatus && context != null && linkURL != null) {
                context.showStatus("");
            }
        }

    }

    /**
     * This is the Action Event handling innerclass.
     */
    class Action implements java.awt.event.ActionListener, 
	java.io.Serializable {

        // Implement ActionListener to catch ActionEvents sent by either 
	// the notifyTimer.

        /**
         * Handles Action events
         * @param e the ActionEvent
         */
        public void actionPerformed(ActionEvent e) {

        // if (e.getSource() == notifyTimer && notifyWhilePressed && 
	// !java.beans.Beans.isDesignTime()) {
        // notified = true;
        // sourceActionEvent();
        // return;
        // }

        }

    }

    /**
     * This is the PropertyChangeEvent handling inner class for 
     * the constrained BevelHeight property.
     * Handles vetoing BevelHeights that are not valid.
     */
    class BevelVeto implements java.beans.VetoableChangeListener, 
        java.io.Serializable {

        /**
         * This method gets called when an attempt to change the constrained 
	 * BevelHeight property is made.
         * Ensures the given bevel size is valid for this button.
         * 
         * @param     e a <code>PropertyChangeEvent</code> object describing the
         * event source and the property that has changed.
         * @exception PropertyVetoException if the recipient wishes the property
         * change to be rolled back.
         */
        public void vetoableChange(PropertyChangeEvent e) 
                throws PropertyVetoException {
            int i = ((Integer) e.getNewValue()).intValue();

        // if (!isValidBevelSize(i)) {
        // throw new PropertyVetoException(
	//    errors.getString("InvalidBevelSize") + i, e);
        // }

        }

    }

    /**
     * This is the PropertyChangeEvent handling inner class for the 
     * constrained Frame property.
     * Handles vetoing Frame strings that are not valid.
     */
    class FrameVeto implements java.beans.VetoableChangeListener, 
        java.io.Serializable {

        /**
         * This method gets called when an attempt to change the constrained 
	 * Frame property is made.
         * Ensures the given Frame string is valid for this button.
         * 
         * @param     e a <code>PropertyChangeEvent</code> object describing the
         * event source and the property that has changed.
         * @exception PropertyVetoException if the recipient wishes the property
         * change to be rolled back.
         */
        public void vetoableChange(PropertyChangeEvent e) 
            throws PropertyVetoException {

            String string = (String) e.getNewValue();

            // if (!isValidFrame(string)) {
            // throw new PropertyVetoException(
	    //     "errors.getString("InvalidFrame") + string, e);
            // }

        }

    }

    /**
     * Fire an action event to the listeners.
     */
    protected void sourceActionEvent() {
        if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(this, 
                    ActionEvent.ACTION_PERFORMED, actionCommand));
        } 
    }

    /**
     * Is the given bevel size valid for this button.
     * @param i the given bevel size
     * @return true if the given bevel size is acceptable, false if not.
     */
    protected boolean isValidBevelSize(int i) {
        Dimension s = getSize();

        if (i < 0 || i >= (s.width / 2) || i >= (s.height / 2)) {
            return false;
        } 

        return true;
    }

    /**
     * Is the given frame string valid.
     * @param string the given frame
     * @return true if the given frame is acceptable, false if not.
     * To be valid it has to be null, or one of the four strings below:
     */
    protected boolean isValidFrame(String string) {
        if (string == null || string.equals("")) {
            return true;
        } 

        // if (    string.equals(GeneralUtils.frameTarget_self)    ||
        // string.equals(GeneralUtils.frameTarget_parent)    ||
        // string.equals(GeneralUtils.frameTarget_top)        ||
        // string.equals(GeneralUtils.frameTarget_blank)    )
        // return true;
        // else

        return false;
    }

    /**
     * Tell the browser to show the document referenced by the linkURL.
     * If the frame specifier is not null or empty, then tell the browser
     * to open the document with that frame.
     */
    protected void linkToURL() {
        if (context != null && linkURL != null) {
            if (frame == null || frame.length() == 0) {
                context.showDocument(linkURL);
            } else {
                context.showDocument(linkURL, frame);
            }
        }
    }

    /**
     * Called after addNotify to set the internally constrined properties 
     * to their temporary values to validate them now that the component 
     * has been added to the form.  This is used to avoid code-gen order 
     * dependencies, since VC generates all property manipulating code 
     * before adding the component to its container.  Subclasses should 
     * override this function for any internally constrained properties,
     * and call the super version in the overridden version.
     */
    protected void verifyContstrainedPropertyValues() {
        try {
            setBevelHeight(tempBevelHeight);
        } catch (PropertyVetoException exc) { /* Silently verify. */}
    }

    /**
     * Sets the applet context used to view documents.
     * @param c the new applet context
     */
    protected void setAppletContext(AppletContext c) {
        context = c;
    }

    /**
     * Maintains the buttonImage size and draws the
     * button in the buttonImage offscreen image.
     * @see #paint
     */
    protected void updateButtonImage() {
        Dimension s = getSize();
        int width = s.width;
        int height = s.height;
        int x = bevel + 1;
        int y = bevel + 1;
        int w = width - 1;
        int h = height - 1;
        int i;
        Color highlight1, highlight2, fillColor, tempBorderColor;
        boolean raised = !(pressed && inButton);

        if (isButtonImageInvalid()) {
            buttonImage = createImage(width, height);

            try {
                MediaTracker tracker = new MediaTracker(this);

                tracker.addImage(buttonImage, 0);
                tracker.waitForID(0);
            } catch (InterruptedException e) {}
        }

        buttonImageGraphics = buttonImage.getGraphics();

        Color oldColor = buttonImageGraphics.getColor();

        if (isEnabled()) {      // Enabled
            tempBorderColor = borderColor;

            if (raised) {
                fillColor = buttonColor;
                highlight1 = hilightColor;
                highlight2 = shadowColor;
            } else {            // Pressed
                fillColor = pressedButtonColor;
                highlight1 = pressedHilightColor;
                highlight2 = pressedShadowColor;
            }
        } else {                // Disabled
            tempBorderColor = disabledBorderColor;
            fillColor = disabledButtonColor;
            highlight1 = disabledHilightColor;
            highlight2 = disabledShadowColor;
        }
        if (!raised && useOffset) {

            // Fill the button content

            buttonImageGraphics.setColor(fillColor);
            buttonImageGraphics.fillRect(x, y, w - x, h - y);

            // Draw the bevels

            buttonImageGraphics.setColor(highlight1);

            for (i = 1; i <= bevel; i++) {
                buttonImageGraphics.drawLine(i, i, i, h);
                buttonImageGraphics.drawLine(i, i, w, i);
            }
        }
        if (raised ||!useOffset) {

            // Fill the button content

            buttonImageGraphics.setColor(fillColor);
            buttonImageGraphics.fillRect(x, y, w - x, h - y);

            // Draw the bevels

            buttonImageGraphics.setColor(highlight1);

            for (i = 1; i <= bevel; i++) {
                buttonImageGraphics.drawLine(i, i, i, h - i);
                buttonImageGraphics.drawLine(i, i, w - i, i);
            }

            buttonImageGraphics.setColor(highlight2);

            for (i = 1; i <= bevel; ++i) {
                buttonImageGraphics.drawLine(i, h - i, w - i, h - i);
                buttonImageGraphics.drawLine(w - i, i, w - i, h - i);
            }
        }

        // Draw the border

        buttonImageGraphics.setColor(tempBorderColor);
        buttonImageGraphics.drawLine(1, 0, w - 1, 0);
        buttonImageGraphics.drawLine(0, 1, 0, h - 1);
        buttonImageGraphics.drawLine(1, h, w - 1, h);
        buttonImageGraphics.drawLine(w, h - 1, w, 1);

        if (hasFocus && showFocus) {
            buttonImageGraphics.setColor(java.awt.Color.darkGray);

            for (x = 3; x <= w - 3; x += 3) {
                buttonImageGraphics.drawLine(x, 3, x + 1, 3);
            }
            for (y = 3; y <= h - 3; y += 3) {
                buttonImageGraphics.drawLine(3, y, 3, y + 1);
            }
            for (x = 3; x <= w - 3; x += 3) {
                buttonImageGraphics.drawLine(x, h - 3, x + 1, h - 3);
            }
            for (y = 3; y <= h - 3; y += 3) {
                buttonImageGraphics.drawLine(w - 3, y, w - 3, y + 1);
            }
        }

        // !!! LAB !!! This should be changed to setClip when it works.
        // Set the clipping area to be the inside of the button.

        buttonImageGraphics.clipRect(bevel + 1, bevel + 1, 
                                     width - bevel - bevel - 2, 
                                     height - bevel - bevel - 2);

        // Restore the original color

        buttonImageGraphics.setColor(oldColor);
    }

    /**
     * Returns true if a button image has been set, but it is not the
     * size of this component.
     */
    protected boolean isButtonImageInvalid() {
        Dimension s = getSize();

        return (buttonImage == null || s.width != buttonImage.getWidth(this) 
                || s.height != buttonImage.getHeight(this));
    }

    /**
     * True if the button is currently pressed.
     */
    transient protected boolean pressed;

    /**
     * True if the button has been released.
     */
    transient protected boolean released;

    /**
     * True if the mouse is over this button.
     */
    transient protected boolean inButton;

    /**
     * If true the button will continuously post events while pressed.
     */
    protected boolean notifyWhilePressed;

    /**
     * True if the notify timer is running.
     */
    transient protected boolean running;

    /**
     * True if a notification has been posted in response to a mouse down.
     */
    transient protected boolean notified;

    /**
     * If true show the focus when the mouse enters the button.
     */
    protected boolean showFocus = false;

    /**
     * If true set pressedAdjustment accordingly, else, it is always 0.
     */
    protected boolean useOffset;

    /**
     * If true show the linkURL in the status area when the mouse is over 
     * the button.  If the linkURL is null, nothing is displayed, 
     * regardless of this flag.
     * This flag also controls erasing of the status area after the URL 
     * has been displayed.
     */
    protected boolean showURLStatus;

    /**
     * Keeps track of wheather or not the button is added to a container.
     * Check before attempting to getFontMetrics() to avoid getting a 
     * null pointer.
     */
    transient protected boolean isAdded;

    /**
     * The "height" (cross-section) of a beveled edge, in pixels.
     */
    protected int bevel;
    protected int tempBevelHeight;

    /**
     * The delay in milliseconds between notifications while the button 
     * is pressed.
     */
    protected int notifyDelay;

    /**
     * A drawing location adjustment for the 3-D bevel while button is pressed.
     */
    protected int pressedAdjustment;

    /**
     * Frame specifier for showing a URL document in a browser or applet
     * viewer. It is interpreted as follows:
     * <UL>
     * <DT>"_self"  show document in the current frame</DT>
     * <DT>"_parent"    show document in the parent frame</DT>
     * <DT>"_top"   show document in the topmost frame</DT>
     * <DT>"_blank" show document in a new unnamed toplevel window</DT>
     * <DT>all others   show document in a new toplevel window with the 
     * given name</DT>
     * </UL>
     */
    protected String frame = null;

    /**
     * The color of the border around the button.
     */
    protected Color borderColor;

    /**
     * The color of the content of the button.  The highlights are derived 
     * from this color.
     */
    protected Color buttonColor;

    /**
     * The offscreen buffer to draw the button in.
     */
    transient protected Image buttonImage = null;

    /**
     * The Graphics of the offscreen buffer to draw the button in.
     */
    transient protected Graphics buttonImageGraphics = null;

    /**
     * The URL of the document to show when the button is clicked.
     */
    protected URL linkURL = null;

    /**
     * Applet context that shows the document.
     */
    transient protected AppletContext context = null;

    // transient protected ResourceBundle errors;

    String actionCommand;
    ActionListener actionListener = null;
    transient boolean hasFocus = false;
    private Color hilightColor = null;
    private Color pressedHilightColor = null;
    private Color disabledHilightColor = null;
    private Color shadowColor = null;
    private Color pressedShadowColor = null;
    private Color disabledShadowColor = null;
    private Color disabledBorderColor = null;
    private Color disabledButtonColor = null;
    private Color pressedButtonColor = null;
    private Key key = null;
    private Focus focus = null;
    private Action action = new Action();
    private Mouse mouse = null;
    private BevelVeto bevelVeto = null;
    private FrameVeto frameVeto = null;
    private java.beans.VetoableChangeSupport vetos = 
        new java.beans.VetoableChangeSupport(this);
    private java.beans.PropertyChangeSupport changes = 
        new java.beans.PropertyChangeSupport(this);

}
