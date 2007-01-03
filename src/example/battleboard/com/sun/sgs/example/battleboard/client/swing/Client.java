/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.battleboard.client.swing;

import com.sun.gi.apps.battleboard.BattleBoard;
import com.sun.gi.apps.battleboard.client.BattleBrain;
import com.sun.gi.apps.battleboard.client.Display;
import com.sun.gi.apps.battleboard.client.RandomBrain;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;

public class Client extends JFrame implements Display, MoveListener {

    private static final long serialVersionUID = 1L;

    /** the delegate for generating moves, or null to use the UI */
    private BattleBrain delegate;
    
    /** the array of raw BattleBoards */
    private BattleBoard[] boards;
    
    /** a corresponding array of display boards */
    private DisplayBoard[] displayBoards;
    
    /** the index of the board of the current player, or -1 for none */
    private int currentPlayer = -1;
    
    /** the messages area */
    private Messages messages;
    
    /** the boards area */
    private BoardSet boardset;
    
    /** queued move (only one move) */
    private String[] queuedMove;
    private Object queueLock = new Object();
    
    /**
     * Creates a new instance of <code>Client</code>:  a
     * <code>JFrame</code> that holds <code>BattleBoard</code>s and
     * displays messages.
     *
     * @param boards an array of <code>BattleBoard</code>s to be
     * displayed.
     */
    public Client(BattleBoard[] boards) {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initGUI();
        setBoards(boards);
        pack();
        setVisible(true);
    }
    
    /**
     * Creates the GUI for the Client.  The GUI will be restructured
     * every time <code>setBoards()</code> is called.
     */
    private void initGUI() {
        boardset = new BoardSet();
        boardset.setBorder(BorderFactory.createMatteBorder(20, 20, 20, 20,
                    Color.black));
        messages = new Messages();
        getContentPane().add(boardset, BorderLayout.CENTER);
        getContentPane().add(messages, BorderLayout.SOUTH);
    }
     
    /**
     * Replaces any currently displayed <code>BattleBoard</code>s with
     * the suplied set.
     *
     * @param boards an array of <code>BattleBoard</code>s, one
     * corresponding to each player.
     */
    public void setBoards(BattleBoard[] boards) {
        this.boards = boards;
        this.displayBoards = new DisplayBoard[boards.length];
        for (int i = 0; i < boards.length; i++) {
            displayBoards[i] = new DisplayBoard(boards[i], boardset);
            displayBoards[i].addMoveListener(this);
        }
        boardset.setBoards(displayBoards);
    }
    
    /**
     * Gets the index (into <code>boards</code> or
     * <code>displayBoards</code>) of the specified named player.
     *
     * @param name the name of the player (may be <code>null</code>)
     *
     * @return the index of that player, or -1 if <code>name</code>
     * was null or a board specifying that name was not found.
     */
    private int getPlayerIndex(String name) {
        for (int i = 0; i < boards.length; i++) {
            BattleBoard board = boards[i];
            if ((board != null) && board.getPlayerName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Sets the <code>BattleBrain</code> delegate that will provide
     * moves.  <p> If <code>delegate</code> is set to
     * <code>null</code> (the default), the user must generate the
     * moves from the interface.
     *
     * @param delegate a BattleBrain delegate for generating move
     * commands, or null to allow the user to generate moves through
     * the UI.
     */
    public void setBrainDelegate(BattleBrain delegate) {
        this.delegate = delegate;
    }
    
    /**
     * Updates the display to show all of the boards, highlighting the
     * active player (if any).  <p>
     *
     * If <code>activePlayer</code> is <code>null</code>, then no
     * board is highlighted.
     *
     * @param activePlayer the name of the "activePlayer" (the player
     * whose board to highlight.
     */
    public void showBoards(String activePlayer) {
        int idx = getPlayerIndex(activePlayer);
        if (idx != currentPlayer) {
            if (currentPlayer >= 0) {
                displayBoards[currentPlayer].setActive(false);
            }
            currentPlayer = idx;
            if (currentPlayer >= 0) {
                displayBoards[currentPlayer].setActive(true);
            }
        }
    }
    
    /**
     * Removes a player from the display.
     *
     * @param playerName the name of the player to remove
     */
    public void removePlayer(String playerName) {
        // figure out which BattleBoard to invalidate
        int idx = getPlayerIndex(playerName);
        if (idx >= 0) {
            displayBoards[idx].endBoard();
            boards[idx] = null;
        }
    }

    /**
     * Displays a message.
     *
     * @param message the String to display
     */
    public void message(String message) {
        // System.out.println(message);
        messages.addMessage(message);
    }
 
    /**
     * Waits for the user to close the window.
     */
    public void gameOver() {
        // no-op
    }

    /**
     * Gets a move from the user and returns the Strings corresponding
     * to the tokens in that move.  <p>
     *
     * This method is responsible for validating the input provided by
     * the user; it should not return until it has valid input.
     *
     * @return an array of Strings representing the operator ("pass"
     * or the name of the player to bomb) and the operands.
     */
    public String[] getMove() {
        if (delegate != null) {
            // there's a BattleBrain delegate.   Ask it for a move.
            return delegate.getMove(boards);
        } else {
            // wait for a move from the user
            String[] move = new String[] {"pass"};
            synchronized(queueLock) {
                if (queuedMove == null) {
                    try {
                        queueLock.wait();
                        move = queuedMove;
                    } catch (InterruptedException ie) {}
                }
                queuedMove = null;
            }
            return move;
        }
    }
    
    /**
     * Gets notified when the user makes a move from the UI
     */
    public void moveMade(String[] move) {
        synchronized(queueLock) {
            queuedMove = move;
            queueLock.notifyAll();
        }
    }

    public static void main(String[] args) {
        String names[] = new String[] { "Alice", "Bob", "Carol", "Eve" };
        BattleBoard bb[] = new BattleBoard[names.length];

        for (int i = 0; i < names.length; i++) {
            bb[i] = new BattleBoard(names[i], 10, 10, 4);
            bb[i].populate();
        }
        Client c = new Client(bb);
        if (args.length > 0) {
            c.setBrainDelegate(new RandomBrain());
        }
        c.showBoards(names[0]);
        c.message("Let the match begin!");
        int counter = -1;
        while (true) {
            int check = 0;
            do {
                counter = (counter + 1) % bb.length;
                if (check++ > bb.length) {
                    System.out.println("DONE!");
                    return;
                }
            } while (bb[counter] == null);
            c.showBoards(names[counter]);
            String move[] = c.getMove();
	    if (move.length == 3) {
		for (int i = 0; i < bb.length; i++) {
		    if (bb[i] != null && 
			    bb[i].getPlayerName().equals(move[0])) {
			int x = Integer.parseInt(move[1]);
			int y = Integer.parseInt(move[2]);
			bb[i].bombBoardPosition(x, y);
			if (bb[i].lost()) {
			    c.removePlayer(move[0]);
			}
			break;
		    }
		}
	    }
            try {
                Thread.sleep(600);
            } catch (InterruptedException ie) {}
        }
    }
}
