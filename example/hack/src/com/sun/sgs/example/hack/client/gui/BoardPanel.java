/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.BoardListener;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;

import java.awt.image.BufferedImage;

import java.util.Map;

import javax.swing.JPanel;


/**
 * This panel renders a dungeon level, and accepts input from the player.
 */
class BoardPanel extends JPanel implements BoardListener
{

    private static final long serialVersionUID = 1;

    // the sprite map used to render the current board
    private Map<Integer,Image> spriteMap;

    // the off-screen copy of the graphics
    private BufferedImage offscreen;

    // the size of each sprite
    private int spriteSize = 0;

    /**
     * Creates an instance of <code>BoardPanel</code>.
     */
    public BoardPanel() {
        // create a temporary buffer for use until we get a real board
        offscreen = new BufferedImage(50, 50,
                                      BufferedImage.TYPE_INT_RGB);
        Graphics g = offscreen.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        g.dispose();
    }

    /**
     * Display the screen that is used until the board is ready.
     */
    public void showLoadingScreen() {
        offscreen = new BufferedImage(getWidth(), getHeight(),
                                      BufferedImage.TYPE_INT_RGB);
        Graphics g = offscreen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        g.setColor(Color.BLACK);
        g.drawString("Loading...", 20, 30);
        g.dispose();
    }

    /**
     * Sets the mapping from identifier to image that is used for the
     * current board.
     *
     * @param spriteSize the size of each sprite
     * @param spriteMap the mapping of identifier to image
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {
        this.spriteSize = spriteSize;
        this.spriteMap = spriteMap;
    }

    /**
     * Changes the panel to render the given board.
     *
     * @param board the new board to render
     */
    public void changeBoard(Board board) {
        int w = board.getWidth() * spriteSize;
        int h = board.getHeight() * spriteSize;

        offscreen = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        setSize(w, h);

        Graphics g = offscreen.createGraphics();
        
        if (board.isDark()) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        } else {
            for (int x = 0; x < board.getWidth(); x++)
                for (int y = 0; y < board.getHeight(); y++)
                    updateSpace(g, x, y, board.getAt(x, y));
        }

        g.dispose();
        repaint();
    }

    /**
     * Private helper that renders a single space.
     */
    private void updateSpace(Graphics g, int x, int y, int [] ids) {
        for (int i = 0; i < ids.length; i++)
            g.drawImage(spriteMap.get(ids[i]), x * spriteSize, y * spriteSize,
                        this);
    }

    /**
     * Applies the given updates to the board.
     *
     * @param spaces an array of updates to specific spaces
     */
    public void updateSpaces(BoardSpace [] spaces) {
        Graphics g = offscreen.createGraphics();

        for (int i = 0; i < spaces.length; i++)
            updateSpace(g, spaces[i].getX(), spaces[i].getY(),
                        spaces[i].getIdentifiers());

        g.dispose();
        repaint();
    }

    /**
     * Called when there's a message from the server.
     *
     * @param message the message sent from the server
     */
    public void hearMessage(String message) {
        // FIXME: this should be painted on the board somewhere, but for
        // now we'll just print it out
        System.out.println(message);
    }

    /**
     * Called to update the on-screen display of the board.
     *
     * @param g the graphcs object to draw onto
     */
    public void paint(Graphics g) {
        g.drawImage(offscreen, 0, 0, this);
    }

}
