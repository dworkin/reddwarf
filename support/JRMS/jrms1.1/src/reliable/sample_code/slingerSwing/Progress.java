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

/*
 * Progress.java
 * 
 * Module Description:
 * 
 * Progress is a horizontal thermometer-like canvas.The mercury
 * level of the thermometer can be set using the setLevel method.
 * Progress can be used to display any progress information.
 */
package com.sun.multicast.reliable.applications.slinger;

import java.awt.*;

/*
 * Package-local class.
 */

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class Progress extends Canvas {

    /*
     * The constructor takes in the width and height of the thermometer
     * as arguments.
     */

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @param w
     * @param h
     *
     * @see
     */
    Progress(int w, int h) {
        width = w;
        height = h;
    }

    /*
     * Method to set the mercury level in the thermometer.
     * Re-painting is done every time the method is called.
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param l
     *
     * @see
     */
    void setLevel(int l) {
        level = l;

        Graphics g = getGraphics();

        g.setColor(Color.white);
        g.drawRect(10, 5, width, height);
        g.setColor(Color.red);
        g.fillRect(10, 5, level, height);
    }

    /*
     * Method to return the preferred size of the thermometer.
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    private int level = 0;
    private int height;
    private int width;
}

