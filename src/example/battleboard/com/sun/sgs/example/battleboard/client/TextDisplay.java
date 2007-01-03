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

package com.sun.gi.apps.battleboard.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

import com.sun.gi.apps.battleboard.BattleBoard;
import com.sun.gi.apps.battleboard.BattleBoard.PositionValue;

public class TextDisplay implements Display {

    private final List<BattleBoard> boards;
    private final List<String> players;
    private final int boardWidth;
    private final int boardHeight;

    /**
     * Creates a text-based display for the given list of boards.
     */
    public TextDisplay(List<BattleBoard> boards) {
        if (boards == null) {
            throw new NullPointerException("boards must not be null");
        }
        if (boards.size() == 0) {
            throw new IllegalArgumentException("boards must not be empty");
        }

        this.boards = new LinkedList<BattleBoard>(boards);
        this.players = new LinkedList<String>();

        for (BattleBoard board : boards) {
            this.players.add(board.getPlayerName());
        }

        BattleBoard firstBoard = this.boards.get(0);

        this.boardWidth = firstBoard.getWidth();
        this.boardHeight = firstBoard.getHeight();
    }

    public void removePlayer(String playerName) {
        if (playerName == null) {
            return;
        }

        players.remove(playerName);

        BattleBoard victim = null;
        for (BattleBoard board : boards) {
            if (board.getPlayerName().equals(playerName)) {
                victim = board;
                break;
            }
        }
        if (victim != null) {
            boards.remove(victim);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void showBoards(String activePlayer) {
        if (boards == null) {
            throw new NullPointerException("boards is null");
        }

        if (boards.size() == 0) {
            throw new IllegalArgumentException("no boards to display");
        }

        if (boards.size() > 3) {
            throw new IllegalArgumentException("too many boards to display");
        }

        System.out.println();
        int index = 1;
        for (BattleBoard currBoard : boards) {
            int remaining = currBoard.getSurvivingCities();
            String survivors;

            if (remaining > 1) {
                survivors = remaining + " surviving cities";
            } else {
                survivors = "1 surviving city";
            }

            System.out.println("player " + index++ + ": " + survivors
                    + ", name: " + currBoard.getPlayerName());
        }

        System.out.println("active: " + activePlayer);
        System.out.println();

        printActiveLine(boards, activePlayer);

        BattleBoard firstBoard = boards.get(0);
        for (int j = firstBoard.getHeight() - 1; j >= 0; j--) {
            for (BattleBoard currBoard : boards) {

                System.out.print(j);
                if (currBoard.getPlayerName().equals(activePlayer)) {
                    System.out.print(" *");
                } else {
                    System.out.print(" |");
                }

                for (int i = 0; i < currBoard.getWidth(); i++) {
                    PositionValue value = currBoard.getBoardPosition(i, j);
                    System.out.print(valueToString(value));
                }

                if (currBoard.getPlayerName().equals(activePlayer)) {
                    System.out.print("* ");
                } else {
                    System.out.print("| ");
                }
            }
            System.out.println();
        }

        printActiveLine(boards, activePlayer);

        for (int i = 0; i < boards.size(); i++) {
            System.out.print("   ");
            for (int j = 0; j < firstBoard.getWidth(); j++) {
                System.out.print(" " + j + " ");
            }
            System.out.print("  ");
        }
        System.out.println();
        System.out.flush();
    }

    /**
     * Displays a single board.
     * <p>
     * 
     * Intended for debugging purposes. Not part of the {@link Display}
     * interface.
     * 
     * @param board the board to display
     */
    public void showBoard(BattleBoard board) {

        System.out.println("-- " + board.getPlayerName() + " -- "
                + board.getSurvivingCities() + " surviving cities");

        for (int j = board.getHeight() - 1; j >= 0; j--) {

            System.out.print(j);

            for (int i = 0; i < board.getWidth(); i++) {
                PositionValue value = board.getBoardPosition(i, j);
                System.out.print(valueToString(value));
            }
            System.out.println();
        }
        System.out.print(" ");

        for (int i = 0; i < board.getWidth(); i++) {
            System.out.print(" " + i + " ");
        }
        System.out.println();
        System.out.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void message(String message) {
        if (message == null) {
            throw new NullPointerException("message is null");
        }
        System.out.print(message);
        System.out.println();
        System.out.flush();
    }

    /**
     * {@inheritDoc}
     */
    public String[] getMove() {
        String[] move;

        for (;;) {
            move = getKeyboardInputTokens("player x y, or pass ");

            if ((move.length == 1) && "pass".equals(move[0])) {
                return move;
            } else if (move.length == 3) {
                if (!players.contains(move[0])) {
                    message("Player " + move[0] + " is not in the game.");
                } else {
                    int x = Integer.parseInt(move[1]);
                    int y = Integer.parseInt(move[2]);

                    if ((x < 0) || (x >= boardWidth) && (y < 0)
                            && (y >= boardHeight)) {
                        message("Illegal position.");
                    } else {
                        return move;
                    }
                }
            } else {
                message("Illegal move.");
            }

            message("  Please try again.\n");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void gameOver() {
        System.exit(0);
    }

    /**
     * Prints the lines at the top and bottom of the boards display that
     * show which board is "highlighted". Used by showBoards.
     * 
     * If <code>activePlayer</code> is <code>null</code> then no
     * board is highlighted.
     * 
     * @param boardList the list of boards being displayed
     * 
     * @param activePlayer the name of the player (if any) whose board
     * is active and therefore highlighted
     */
    private static void printActiveLine(List<BattleBoard> boardList,
            String activePlayer) {

        System.out.print("   ");
        for (BattleBoard currBoard : boardList) {
            String str = (currBoard.getPlayerName().equals(activePlayer))
                    ? "***" : "---";

            for (int i = 0; i < currBoard.getWidth(); i++) {
                System.out.print(str);
            }

            System.out.print("     ");
        }
        System.out.println();
        System.out.flush();
    }

    /**
     * Returns a String that represents a {@link PositionValue}.
     * 
     * @param value the PositionValue
     * 
     * @return a String to display for that value
     */
    private String valueToString(PositionValue value) {
        switch (value) {
            case VACANT:
                return "   ";
            case CITY:
                return " C ";
            case UNKNOWN:
                return " ? ";
            case MISS:
                return " m ";
            case NEAR:
                return " N ";
            case HIT:
                return " H ";
            default:
                return "???";
        }
    }

    /**
     * Prompts the user for input (via <code>System.out</code>),
     * reads a line of input from <code>System.in</code>, splits the
     * line into tokens by whitespace and returns the tokens as an array
     * of Strings. If the prompt is <code>null</code>, then a default
     * prompt of <code>"&gt;&gt;"</code> is used.
     * <p>
     * 
     * If an exception occurs, a zero-length array is returned.
     * <p>
     * 
     * @param prompt the prompt to give the user
     * 
     * @return an array of Strings containing the tokens in the next
     * line of input from <code>System.in</code>, or an empty array
     * if any errors occur
     */
    private String[] getKeyboardInputTokens(String prompt) {
        String commandline = "";

        if (prompt == null) {
            prompt = ">> ";
        }

        for (;;) {
            System.out.print(prompt);
            System.out.println();
            System.out.flush();

            try {
                commandline = getKeyboardLine();
            } catch (IOException e) {
                System.out.println("Unexpected exception: " + e);
                System.out.flush();
                return new String[0];
            }

            if (commandline == null) {
                return new String[0];
            } else if (commandline.length() > 0) {
                return commandline.split("\\s+");
            }
        }
    }

    /**
     * Reads a line of input from System.in (which for the purpose of
     * this game, we assume is a players keyboard) and returns it as a
     * String.
     * 
     * @return the next line of text read from <code>System.in</code>.
     * 
     * @throws IOException if an exception occurs accessing
     * <code>System.in</code>.
     */
    private String getKeyboardLine() throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(
                System.in));
        return input.readLine();
    }
}
