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

package com.sun.multicast.reliable.transport.tram;

import java.awt.Frame;
import java.awt.Button;
import java.awt.Dimension;
import java.awt.Color;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

/**
 * This class adds a brick to a wall each time the addBrick method
 * is called. It's pretty crude but gives a visual for seeing packets
 * received at an stp client.
 */
class BrickLayer extends Frame {
    Button brickList[];
    int xpos, ypos;
    static Dimension brickSize = new Dimension(60, 20);
    boolean half = false;

    public BrickLayer(Dimension frameSize, int totalBricks, String title) {
        this.setSize(frameSize);
        this.setBackground(Color.black);
        this.setForeground(Color.white);
        this.setTitle(title);

        brickList = new Button[totalBricks + 1];
        xpos = -brickSize.width;
        ypos = getSize().height - brickSize.height;

        setVisible(true);
        show();
    }

    public static void main(String args[]) {
        Dimension frameSize = new Dimension(600, 600);
        int count = 100;
        BrickLayer source = new BrickLayer(frameSize, count, "Source");
        BrickLayer sink = new BrickLayer(frameSize, 0, "Sink");

        for (int i = 1; i <= count; i++) {
            source.addBrick(i);
        }

        try {
            FileOutputStream out = new FileOutputStream("/tmp/brickFile.tmp");
            FileInputStream in = new FileInputStream("/tmp/brickFile.tmp");

            for (int i = count; i > 0; i--) {
                source.getBrick(i, out);

                try {
                    Thread.sleep(500);
                } catch (Exception e) {}
                sink.putBrick(in);
            }
        } catch (Exception ee) {
            ee.printStackTrace();
        }

        source.dispose();
        sink.dispose();
        System.exit(1);
    }

    public void addBrick(int brickNumber) {
        xpos += brickSize.width;

        if (xpos >= getSize().width) {
            ypos -= brickSize.height;

            if (half) {
                half = false;
                xpos = 0;
            } else {
                half = true;
                xpos = -(brickSize.width / 2);
            }
        }

        Button b = new Button(String.valueOf(brickNumber));

        b.setLocation(xpos, ypos);
        b.setSize(brickSize);
        b.setBackground(Color.red);
        add(b);
        repaint();

        brickList[brickNumber] = b;
    }

    public void removeBrick(int brickNumber) {
        remove(brickList[brickNumber]);

        brickList[brickNumber] = null;

        repaint();
    }

    public void getBrick(int brickNumber, OutputStream os) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(os);

            oos.writeObject(brickList[brickNumber]);
            oos.flush();
            removeBrick(brickNumber);

            oos = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void putBrick(InputStream is) 
            throws IOException, ClassNotFoundException {
        ObjectInputStream ios = new ObjectInputStream(is);
        Button b = (Button) ios.readObject();

        this.add(b);
        repaint();
    }

}

