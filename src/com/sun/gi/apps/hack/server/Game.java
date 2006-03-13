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


/*
 * Game.java
 *
 * Created by: seth proctor (stp)
 * Created on: Mon Feb 20, 2006	 6:11:45 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimTask;

import java.nio.ByteBuffer;


/**
 * This is the generic interface for a game. A game is a place where users
 * can interact with each other and with the game's logic. Typically the games
 * that players use are the <code>Lobby</code> or a <code>Dungeon</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Game extends GLO
{

    /**
     * The standard namespace prefix for all games.
     */
    public static final String NAME_PREFIX = "game:";

    /**
     * Joins the given <code>Player</code> to this <code>Game</code>. The
     * player has been get-locked.
     *
     * @param player the <code>Player</code> joining this game
     */
    public void join(Player player);

    /**
     * Removes the given <code>Player</code> from this <code>Game</code>. The
     * player has been get-locked.
     *
     * @param player the <code>Player</code> joining this game
     */
    public void leave(Player player);

    /**
     * Creates a new instance of a <code>MessageHandler</code> that will
     * handle messages intended for this <code>Game</code>. The intent is
     * that this instance can be part of some other <code>GLO</code>'s
     * internal state, like a <code>Player</code>, so that contention is
     * moved there but is still being driven by logic defined by this
     * <code>Game</code> implementation.
     *
     * @return a new instance of <code>MessageHandler</code>
     */
    public MessageHandler createMessageHandler();

    /**
     * Returns the name of this <code>Game</code>.
     *
     * @return the name
     */
    public String getName();

    /**
     * Returns the number of players in this <code>Game</code>.
     *
     * @return the player count
     */
    public int numPlayers();

}
