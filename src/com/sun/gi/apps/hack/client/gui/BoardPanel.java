
/*
 * BoardPanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Feb 16, 2006	 2:56:46 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.BoardListener;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;

import java.awt.image.BufferedImage;

import java.util.Map;

import javax.swing.JPanel;


/**
 *
 */
class BoardPanel extends JPanel implements BoardListener
{

    //
    private Map<Integer,Image> spriteMap;

    //
    private BufferedImage offscreen;

    //
    private int spriteSize = 0;

    /**
     *
     */
    public BoardPanel() {
        offscreen = new BufferedImage(50, 50,
                                      BufferedImage.TYPE_INT_RGB);
        Graphics g = offscreen.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        g.dispose();
    }

    /**
     *
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
     *
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {
        this.spriteSize = spriteSize;
        this.spriteMap = spriteMap;
    }

    /**
     *
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
     *
     */
    private void updateSpace(Graphics g, int x, int y, int [] ids) {
        for (int i = 0; i < ids.length; i++)
            g.drawImage(spriteMap.get(ids[i]), x * spriteSize, y * spriteSize,
                        this);
    }

    /**
     *
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
     *
     */
    public void hearMessage(String message) {
        // FIXME: this should be painted on the board somewhere, but for
        // now we'll just print it out
        System.out.println(message);
    }

    /**
     *
     */
    public void paint(Graphics g) {
        g.drawImage(offscreen, 0, 0, this);
    }

}
