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
 * bricks.java
 * 
 * Module Description:
 * 
 * Build a brick wall.
 */
package com.sun.multicast.reliable.transport.tram;

import java.awt.*;
import java.util.Vector;

/**
 * This class adds a brick to a wall each time the addBrick method
 * is called. It's pretty crude but gives a visual for seeing packets
 * received at an stp client.
 */
class bricks extends Frame {
    Dimension brick = new Dimension(60, 20);
    boolean brickList[] = new boolean[100];
    int nextBrick = 1;
    int xpos, ypos, bricksPerRow;
    Dimension d;
    Image bufferedImage;
    Graphics bg;

    public bricks(Dimension d, String name) {
        super(name);

        this.d = d;

        setSize(d);
        show();

        ypos = d.height;
        bricksPerRow = d.width / brick.width;
    }

    public void addBrick(int brickNumber) {
        if (brickNumber >= brickList.length) {
            System.out.println("BrickNumber = " + brickNumber 
                               + " list length = " + brickList.length);

            boolean tmp[] = new boolean[brickList.length * 2];

            System.arraycopy(brickList, 0, tmp, 0, brickList.length);

            brickList = tmp;
            tmp = null;

            System.out.println("New brickList Length = " + brickList.length);
        }

        for (; nextBrick < brickNumber; nextBrick++) {
            brickList[nextBrick] = false;
        }

        brickList[brickNumber] = true;

        if (nextBrick == brickNumber) {
            nextBrick++;
        } 

        drawBricks();
    }

    public void removeBrick(int brickNumber) {
        if (brickNumber < brickList.length) {
            brickList[brickNumber] = false;
        }

        drawBricks();
    }

    public void drawBricks() {
        if (bufferedImage == null) {
            bufferedImage = createImage(getSize().width, getSize().height);
        } 

        bg = bufferedImage.getGraphics();

        bg.setColor(Color.red);

        xpos = 0;

        boolean halfBrick = false;

        ypos = getSize().height - brick.height;

        for (int i = 1; i < nextBrick; i++) {
            if (brickList[i]) {
                bg.fill3DRect(xpos, ypos, brick.width, brick.height, true);
            } else {
                bg.clearRect(xpos, ypos, brick.width, brick.height);
            }

            xpos += brick.width;

            if (xpos > getSize().width) {
                if (halfBrick) {
                    halfBrick = false;
                    xpos = 0;
                } else {
                    halfBrick = true;
                    xpos = -(brick.width / 2);
                }

                ypos -= brick.height;

                if (ypos < 0) {
                    bufferedImage = createImage(getSize().width, 
                                                getSize().height);
                    bg = bufferedImage.getGraphics();

                    bg.setColor(Color.red);

                    ypos = getSize().height - brick.height;
                }
            }
        }

        repaint();
    }

    public void update(Graphics g) {
        invalidate();
        paint(g);
    }

    public void paint(Graphics g) {

        // System.out.println("Painting image");

        if (bufferedImage != null) {
            g.drawImage(bufferedImage, 0, 0, null);
        } 
    }

}

