/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.apps.battleboard.server;

import com.sun.gi.logic.GLO;

/**
 * A GLO representing the "history" of a BattleBoard player: their name,
 * and how many games he or she has has won and lost.
 */
public class PlayerHistory implements GLO {

    private static final long serialVersionUID = 1;

    private final String playerName;
    private long gamesWon;
    private long gamesLost;

    /**
     * Create a PlayerHistory object for the player of the given name.
     * <p>
     * 
     * Each player should have exactly one PlayerHistory object created
     * for their name. This is not enforced here; it is enforced in
     * {@link Player#gameStarted gameStarted}, which only creates new
     * PlayerHistory instances for players for which it cannot find a
     * PlayerHistory GLO.
     * 
     * @param name the player name
     */
    public PlayerHistory(String name) {
        this.playerName = name;
        this.gamesWon = 0;
        this.gamesLost = 0;
    }

    /**
     * Returns the number of games won by this player.
     * 
     * @return the number of games won by this player
     */
    public long getWon() {
        return gamesWon;
    }

    /**
     * Returns the number of games lost by this player.
     * 
     * @return the number of games lost by this player
     */
    public long getLost() {
        return gamesLost;
    }

    /**
     * Returns the number of games played by this player.
     * 
     * @return the number of games played by this player
     */
    public long getTotalGames() {
        return gamesWon + gamesLost;
    }

    /**
     * Increment the number of wins by this player.
     */
    public void win() {
        gamesWon++;
    }

    /**
     * Increment the number of losses by this player.
     */
    public void lose() {
        gamesLost++;
    }

    /**
     * Returns a string representing known about this player.
     * 
     * @return a string representing known about this player.
     */
    public String toString() {
        return "playerName: " + playerName + " won: " + gamesWon +
		" lost: " + gamesLost;
    }
}
