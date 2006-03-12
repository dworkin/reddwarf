
/*
 * GamePanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 4:32:31 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.CommandListener;
import com.sun.gi.apps.hack.client.GameManager;

import java.awt.BorderLayout;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JPanel;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GamePanel extends JPanel implements KeyListener
{

    //
    private BoardPanel boardPanel;

    //
    private PlayerInfoPanel playerInfoPanel;

    //
    private CommandListener commListener;

    /**
     *
     */
    public GamePanel(GameManager gameManager) {
        super(new BorderLayout(4, 4), true);

        this.commListener = gameManager;

        // create the panel that manages the game...
        boardPanel = new BoardPanel();
        gameManager.addBoardListener(boardPanel);
        // ...and the panel for the player info
        playerInfoPanel = new PlayerInfoPanel();
        gameManager.addPlayerListener(playerInfoPanel);

        // setup to listen for all keyboard events
        setFocusable(true);
        addKeyListener(this);

        // install the panels into our layout
        add(boardPanel, BorderLayout.CENTER);
        add(playerInfoPanel, BorderLayout.EAST);
    }

    /**
     *
     */
    public void showLoadingScreen() {
        boardPanel.showLoadingScreen();
    }

    /**
     *
     */
    public void keyPressed(KeyEvent e) {
        commListener.action(e.getKeyCode());
    }

    /**
     *
     */
    public void keyReleased(KeyEvent e) {

    }

    /**
     *
     */
    public void keyTyped(KeyEvent e) {
        
    }

}
