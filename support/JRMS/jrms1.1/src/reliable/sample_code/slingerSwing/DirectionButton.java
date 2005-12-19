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

import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
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
public class DirectionButton extends ButtonBase implements 
    java.io.Serializable {

    ColorUtils colorUtils;

    /**
     * The point LEFT style constant.
     */
    public static final int LEFT = 0;

    /**
     * The point RIGHT style constant.
     */
    public static final int RIGHT = 1;

    /**
     * The point UP style constant.
     */
    public static final int UP = 2;

    /**
     * The point DOWN style constant.
     */
    public static final int DOWN = 3;

    /**
     * Constructs a default DirectionButton, which will point left.
     */
    public DirectionButton() {
        this(LEFT);
    }

    /**
     * Constructs a DirectionButton pointing the specified direction.
     * @param d a style constant indicating which direction to point the button
     * @see #LEFT
     * @see #UP
     * @see #RIGHT
     * @see #DOWN
     */
    public DirectionButton(int d) {
        colorUtils = new ColorUtils();
        direction = d;
        left = 0;
        right = 0;
        bottom = 0;
        indent = 0;
        tempIndent = indent;

        try {
            setArrowColor(Color.black);
        } catch (PropertyVetoException exc) {}
    }

    /**
     * Sets the direction of the arrow after construction.
     * @param d constant indicating direction to point button
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #getDirection
     * @see #LEFT
     * @see #UP
     * @see #RIGHT
     * @see #DOWN
     */
    public void setDirection(int d) throws PropertyVetoException {
        if (direction != d) {
            Integer oldValue = new Integer(direction);
            Integer newValue = new Integer(d);

            vetos.fireVetoableChange("direction", oldValue, newValue);

            direction = d;

            repaint();
            changes.firePropertyChange("direction", oldValue, newValue);
        }
    }

    /**
     * Returns the direction the button is currently pointing.
     * @see #setDirection
     * @see #LEFT
     * @see #UP
     * @see #RIGHT
     * @see #DOWN
     */
    public int getDirection() {
        return direction;
    }

    /**
     * Sets the amount of blank space between the arrow and the button
     * border in pixels.
     * @param ai the margin around the arrow in pixels. 0=arrow takes 
     * up entire button
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #getArrowIndent
     */
    public void setArrowIndent(int ai) throws PropertyVetoException {
        if (isAdded) {
            if (indent != ai) {
                Integer oldValue = new Integer(indent);
                Integer newValue = new Integer(ai);

                vetos.fireVetoableChange("arrowIndent", oldValue, newValue);

                indent = ai;
                tempIndent = ai;

                // Make sure that changes to indent don't make changes 
		// to shrinkTriangle give us a bad triangle.

                shrinkTriangle(left, right, top, bottom);
                repaint();
                changes.firePropertyChange("arrowIndent", oldValue, newValue);
            }
        } else {
            // We store the value until we are added then set the value 
	    // to avoid code-gen order dependencies.

            tempIndent = ai;
        }
    }

    /**
     * Sets the color of the direction arrow.
     * @param newValue the new arrow color.
     * @exception PropertyVetoException
     * if the specified property value is unacceptable
     * @see #getArrowColor
     */
    public void setArrowColor(Color newValue) throws PropertyVetoException {
        Color oldValue = arrowColor;

        vetos.fireVetoableChange("arrowColor", oldValue, newValue);

        arrowColor = newValue;

        try {
            disabledArrowColor = colorUtils.fade(arrowColor, Color.lightGray, 
                                                 0.50);
        } catch (IllegalArgumentException exc) {}

        repaint();
        changes.firePropertyChange("arrowColor", oldValue, newValue);
    }

    /**
     * Gets the current color of the direction arrow.
     * @return the current arrow color
     * @see #setArrowColor
     */
    public Color getArrowColor() {
        return arrowColor;
    }

    /**
     * Returns the amount of blank space between the arrow and the button
     * border in pixels.
     * @see #setArrowIndent
     */
    public int getArrowIndent() {
        return isAdded ? indent : tempIndent;
    }

    /**
     * Sets the extra amount, in pixels, to shrink the arrow triangle.
     * Constrains the values such that the arrow will never be less than
     * three pixels.  If a value is entered that would exceed this limit,
     * the limit will be used instead.
     * @param left pixels to shrink from left side
     * @param right pixels to shrink from right side
     * @param top pixels to shrink from top
     * @param bottom pixels to shrink from bottom
     */
    public void shrinkTriangle(int l, int r, int t, int b) {
        if (isAdded) {
            Dimension s;

            try {

                // try jdk 1.2 method

                s = getSize();
            } catch (NoSuchMethodError e) {

                // try jdk 1.1 method

                s = size();
            }

            int maxWidth = s.width - bevel - bevel - 2;
            int maxHeight = s.height - bevel - bevel - 2;

            if (maxWidth - (l + r + indent + indent) >= 3) {
                left = l;
                right = r;
            } else {
                left = (maxWidth - indent - indent - 3) / 2;
                right = left;
            }
            if (maxHeight - (t + b + indent + indent) >= 3) {
                top = t;
                bottom = b;
            } else {
                top = (maxHeight - indent - indent - 3) / 2;
                bottom = top;
            }
        }
    }

    /**
     * Returns the recommended dimensions to properly display this component.
     * This is a standard Java AWT method which gets called to determine
     * the recommended size of this component.
     * 
     * @return a button that has a content area of 7 by 7 pixels.
     * @see java.awt.Component#getMinimumSize
     */
    public Dimension getPreferredSize() {
        Dimension defaultSize = super.getPreferredSize();

        return new Dimension(defaultSize.width + 7, defaultSize.height + 7);
    }

    /**
     * Returns the minimum dimensions to properly display this component.
     * This is a standard Java AWT method which gets called to determine
     * the minimum size of this component.
     * 
     * @return a button that has a content area of 3 by 3 pixels.
     * @see java.awt.Component#getMinimumSize
     */
    public Dimension getMinimumSize() {
        Dimension defaultSize = super.getPreferredSize();

        return new Dimension(defaultSize.width + 3, defaultSize.height + 3);
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

        if (sizeVeto == null) {
            sizeVeto = new SizeVeto();

            addDirectionListener(sizeVeto);
        }
        if (indentVeto == null) {
            indentVeto = new IndntVeto();

            addArrowIndentListener(indentVeto);
        }

        // Add after the listeners are hooked up

        super.addNotify();
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

        if (sizeVeto != null) {
            removeDirectionListener(sizeVeto);

            sizeVeto = null;
        }
        if (indentVeto != null) {
            removeArrowIndentListener(indentVeto);

            indentVeto = null;
        }

        super.removeNotify();
    }

    /**
     * Adds a listener for all event changes.
     * @param listener the listener to add.
     * @see #removePropertyChangeListener
     */
    public synchronized void addPropertyChangeListener(
	PropertyChangeListener listener) {

        super.addPropertyChangeListener(listener);
        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for all event changes.
     * @param listener the listener to remove.
     * @see #addPropertyChangeListener
     */
    public synchronized void removePropertyChangeListener(
	PropertyChangeListener listener) {

        super.removePropertyChangeListener(listener);
        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for all event changes.
     * @param listener the listener to add.
     * @see #removeVetoableChangeListener
     */
    public synchronized void addVetoableChangeListener(
	VetoableChangeListener listener) {

        super.addVetoableChangeListener(listener);
        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for all event changes.
     * @param listener the listener to remove.
     * @see #addVetoableChangeListener
     */
    public synchronized void removeVetoableChangeListener(
	VetoableChangeListener listener) {

        super.removeVetoableChangeListener(listener);
        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a listener for the Direction property changes.
     * @param listener the listener to add.
     * @see #removeDirectionChangeListener
     */
    public synchronized void addDirectionListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for the Direction property changes.
     * @param listener the listener to remove.
     * @see #addDirectionChangeListener
     */
    public synchronized void removeDirectionListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for the Direction property changes.
     * @param listener the listener to add.
     * @see #removeVetoableDirectionChangeListener
     */
    public synchronized void addDirectionListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for the Direction property changes.
     * @param listener the listener to remove.
     * @see #addVetoableDirectionChangeListener
     */
    public synchronized void removeDirectionListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a listener for the ArrowIndent property changes.
     * @param listener the listener to add.
     * @see #removeDirectionChangeListener
     */
    public synchronized void addArrowIndentListener(
	PropertyChangeListener listener) {

        changes.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener for the ArrowIndent property changes.
     * @param listener the listener to remove.
     * @see #addDirectionChangeListener
     */
    public synchronized void removeArrowIndentListener(
	PropertyChangeListener listener) {

        changes.removePropertyChangeListener(listener);
    }

    /**
     * Adds a vetoable listener for the ArrowIndent property changes.
     * @param listener the listener to add.
     * @see #removeVetoableDirectionChangeListener
     */
    public synchronized void addArrowIndentListener(
	VetoableChangeListener listener) {

        vetos.addVetoableChangeListener(listener);
    }

    /**
     * Removes a vetoable listener for the ArrowIndent property changes.
     * @param listener the listener to remove.
     * @see #addVetoableDirectionChangeListener
     */
    public synchronized void removeArrowIndentListener(
	VetoableChangeListener listener) {

        vetos.removeVetoableChangeListener(listener);
    }

    /**
     * This is the PropertyChangeEvent handling inner class for the 
     * constrained Direction property.  Handles vetoing Directions that 
     * are not valid.
     */
    class SizeVeto implements java.beans.VetoableChangeListener, 
        java.io.Serializable {

        /**
         * This method gets called when an attempt to change the constrained 
	 * Direction property is made.  Ensures the given direction size is 
	 * valid for this button.
         * 
         * @param     e a <code>PropertyChangeEvent</code> object describing 
	 * the event source and the property that has changed.
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
     * constrained ArrowIndent property.  Handles vetoing ArrowIndents 
     * that are not valid.
     */
    class IndntVeto implements java.beans.VetoableChangeListener, 
        java.io.Serializable {

        /**
         * This method gets called when an attempt to change the 
         * constrained ArrowIndent property is made.
         * Ensures the given arrow indent size is valid for this button.
         * 
         * @param     e a <code>PropertyChangeEvent</code> object describing the
         * event source and the property that has changed.
         * @exception PropertyVetoException if the recipient wishes the property
         * change to be rolled back.
         */
        public void vetoableChange(PropertyChangeEvent e) 
                throws PropertyVetoException {
            int i = ((Integer) e.getNewValue()).intValue();

        // if (!isValidArrowIndent(i))
        // {
        // throw new PropertyVetoException(
	//     errors.getString("InvalidArrowIndent") + i, e);
        // }

        }
    }

    /**
     * Maintains the buttonImage size and draws the
     * button in the buttonImage offscreen image.
     */
    protected void updateButtonImage() {
        super.updateButtonImage();

        Graphics g = buttonImage.getGraphics();
        Dimension s;

        try {

            // try jdk 1.2 method

            s = getSize();
        } catch (NoSuchMethodError e) {
            s = size();
        }

        int trueBevel = bevel + 1;
        int centerHorizontal;
        int centerVertical;
        int topSide;
        int bottomSide;
        int leftSide;
        int rightSide;

        if (isEnabled()) {
            g.setColor(arrowColor);
        } else {
            g.setColor(disabledArrowColor);
        }

        centerHorizontal = ((s.width - 1) / 2) + pressedAdjustment;
        centerVertical = ((s.height - 1) / 2) + pressedAdjustment;
        topSide = (top + trueBevel - 1) + pressedAdjustment + indent;
        bottomSide = (s.height - 1 - bottom - trueBevel) + pressedAdjustment 
                     - indent;
        leftSide = (left + trueBevel - 1) + pressedAdjustment + indent;
        rightSide = (s.width - 1 - right - trueBevel) + pressedAdjustment 
                    - indent;

        switch (direction) {

        case UP: 
            fillTriangle(g, centerHorizontal, topSide, leftSide, bottomSide, 
                         rightSide, bottomSide, direction);

            break;

        case DOWN: 
            fillTriangle(g, centerHorizontal, bottomSide, leftSide, topSide, 
                         rightSide, topSide, direction);

            break;

        case LEFT: 
            fillTriangle(g, leftSide, centerVertical, rightSide, bottomSide, 
                         rightSide, topSide, direction);

            break;

        case RIGHT: 
            fillTriangle(g, rightSide, centerVertical, leftSide, bottomSide, 
                         leftSide, topSide, direction);

            break;
        }

        if (g != null) {
            g.dispose();
        } 
    }

    /**
     * Fills a triangle which has at least one side that is straight 
     * up and down or left and right.
     * @param g the Graphics to use to draw with.
     * @param tipX the horizontal coordinate of the point opposite 
     * a straight side.
     * @param tipY the vertical coordinate of the point opposite a 
     * straight side.
     * @param aX the horizontal coordinate of one of the two points 
     * defining the straight side.
     * @param aY the vertical coordinate of one of the two points 
     * defining the straight side.
     * @param bX the horizontal coordinate of one of the two points 
     * defining the straight side.
     * @param bY the vertical coordinate of one of the two points 
     * defining the straight side.
     * @param direction the direction of the straight line UP, DOWN, or LEFT, 
     * RIGHT.
     * 
     * aX and bX should be the same for UP or Down.  aY and bY should be 
     * the same for LEFT or RIGHT.  If not, then the a coordinates are used.
     * 
     * @see #UP
     * @see #DOWN
     * @see #LEFT
     * @see #RIGHT
     */
    protected void fillTriangle(Graphics g, int tipX, int tipY, int aX, 
                                int aY, int bX, int bY, int direction) {
        int dist, max, min;

        switch (direction) {

        case UP: 

        case DOWN: 
            dist = Math.abs(aX - bX);
            max = Math.max(aX, bX);
            min = Math.min(aX, bX);

            for (int i = min; i <= max; ++i) {
                g.drawLine(tipX, tipY, i, aY);
            }

            break;

        case RIGHT: 

        case LEFT: 
            dist = Math.abs(aY - bY);
            max = Math.max(aY, bY);
            min = Math.min(aY, bY);

            for (int i = min; i <= max; ++i) {
                g.drawLine(tipX, tipY, aX, i);
            }

            break;
        }
    }

    /**
     * Is the given bevel size valid for this button.
     * @param i the given bevel size
     * @return true if the given bevel size is acceptable, false if not.
     */
    protected boolean isValidBevelSize(int i) {
        Dimension s;

        try {

            // try jdk 1.2 method

            s = getSize();
        } catch (NoSuchMethodError e) {
            s = size();
        }

        int temp = i * 2 + 4;

        if (i < 0 || s.width < temp || s.height < temp) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Is the given direction valid for this button.
     * @param i the given bevel size
     * @return true if the given direction is acceptable, false if not.
     */
    protected boolean isValidDirection(int i) {
        switch (i) {

        case LEFT: 

        case RIGHT: 

        case UP: 

        case DOWN: 
            return true;

        default: 
            return false;
        }
    }

    /**
     * Is the given arrow indent is valid for this button.
     * @param i the given bevel size
     * @return true if the given indent size is acceptable, false if not.
     */
    protected boolean isValidArrowIndent(int i) {
        Dimension s;

        try {

            // try jdk 1.2 method

            s = getSize();
        } catch (NoSuchMethodError e) {
            s = size();
        }

        int temp = (i * 2) + (bevel + 1) * 2 + 4;

        if (i < 0 || s.width < temp || s.height < temp) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Called after addNotify to set the internally constrined properties 
     * to their temporary values to validate them now that the component 
     * has been added to the form.  This is used to avoid code-gen order 
     * dependencies, since VC generates all property manipulating code 
     * before adding the component to its container.
     * Subclasses should override this function for any internally constrained 
     * properties, and call the super version in the overridden version.
     */
    protected void verifyContstrainedPropertyValues() {
        super.verifyContstrainedPropertyValues();

        try {
            setArrowIndent(tempIndent);
        } catch (PropertyVetoException exc) { /* Silently verify. */}
    }

    /**
     * The color of the arrow in the button.
     */
    protected Color arrowColor = null;

    /**
     * The color of the arrow when the button is disabled.
     */
    protected Color disabledArrowColor = null;

    /**
     * The direction the arrow points.
     * One of: LEFT, UP, RIGHT, or DOWN.
     * @see #LEFT
     * @see #UP
     * @see #RIGHT
     * @see #DOWN
     */
    protected int direction;

    /**
     * The number of pixels to shrink the arrow from the left side 
     * of the button.
     */
    protected int left;

    /**
     * The number of pixels to shrink the arrow from the right side 
     * of the button.
     */
    protected int right;

    /**
     * The number of pixels to shrink the arrow from the top side 
     * of the button.
     */
    protected int top;

    /**
     * The number of pixels to shrink the arrow from the bottom side 
     * of the button.
     */
    protected int bottom;

    /**
     * The margin around the arrow in pixels. 0 = arrow takes up entire button.
     */
    protected int indent;
    protected int tempIndent;

    // transient protected ResourceBundle errors;

    private SizeVeto sizeVeto = null;
    private IndntVeto indentVeto = null;
    private java.beans.VetoableChangeSupport vetos = 
        new java.beans.VetoableChangeSupport(this);
    private java.beans.PropertyChangeSupport changes = 
        new java.beans.PropertyChangeSupport(this);

}
