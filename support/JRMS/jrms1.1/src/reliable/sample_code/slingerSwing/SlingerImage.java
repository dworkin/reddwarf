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

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class SlingerImage extends java.awt.Canvas {

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @see
     */
    public SlingerImage() {
        setVisible(true);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param theImage
     *
     * @see
     */
    public void setCurrentImage(Image theImage) {
        currentImage = theImage;
    }

    private Image currentImage = null;

    /**
     * Paints this component using the given graphics context.
     * This is a standard Java AWT method which typically gets called
     * by the AWT to handle painting this component. It paints this component
     * using the given graphics context. The graphics context clipping region
     * is set to the bounding rectangle of this component and its <0,0>
     * coordinate is this component's top-left corner.
     * 
     * @param g the graphics context used for painting
     * @see java.awt.Component#repaint
     * @see #update
     */
    public void paint(Graphics g) {
        if (currentImage != null) {
            g.drawImage(currentImage, 0, 0, this);
        }
    }

    /**
     * Handles redrawing of this component on the screen.
     * This is a standard Java AWT method which gets called by the Java
     * AWT (repaint()) to handle repainting this component on the screen.
     * The graphics context clipping region is set to the bounding rectangle
     * of this component and its <0,0> coordinate is this component's
     * top-left corner.
     * Typically this method paints the background color to clear the
     * component's drawing space, sets graphics context to be the foreground
     * color, and then calls paint() to draw the component.
     * 
     * It is overridden here to make clearing the background before painting
     * optional. If the clearFrame flag is true the background will be erased
     * before painting begins.
     * 
     * @param g the graphics context
     * @see java.awt.Component#repaint
     * @see #paint
     */
    public void update(Graphics g) {
        paint(g);
    }

}

