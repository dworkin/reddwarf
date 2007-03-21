/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.CommandListener;
import com.sun.sgs.example.hack.client.GameManager;

import java.awt.BorderLayout;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JPanel;


/**
 * This is a panel used to show interaction with a dungeon.
 */
public class GamePanel extends JPanel implements KeyListener
{

    private static final long serialVersionUID = 1;

    // the panel that renders the board
    private BoardPanel boardPanel;

    // the panel that shows character detail
    private PlayerInfoPanel playerInfoPanel;

    // the listener used to forward user actions
    private CommandListener commListener;

    /**
     * Creates an instance of <code>GamePanel</code>.
     *
     * @param gameManager the manager used to handle user actions
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
     * Tells the panel to render a screen that shows a loading message.
     */
    public void showLoadingScreen() {
        boardPanel.showLoadingScreen();
    }

    /**
     * Called when the user presses a key. This drives all user interaction.
     *
     * @param e the event describing the key press
     */
    public void keyPressed(KeyEvent e) {
        commListener.action(e.getKeyCode());
    }

    /**
     * Called when the user releases a key. This is ignored, since all
     * actions are triggered from <code>keyPressed</code>.
     *
     * @param e the event describing the key released
     */
    public void keyReleased(KeyEvent e) {

    }

    /**
     * Called when the user types a key. This is ignored, since all
     * actions are triggered from <code>keyPressed</code>.
     *
     * @param e the event describing the key typed
     */
    public void keyTyped(KeyEvent e) {
        
    }

}
