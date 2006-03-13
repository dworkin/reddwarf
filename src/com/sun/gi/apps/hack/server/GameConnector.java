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
 * GameConnector.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Mar  3, 2006	 9:51:41 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.Connector;
import com.sun.gi.apps.hack.server.level.Level;


/**
 * This is a <code>Connector</code> that is used to connect a
 * <code>game</code> to an initial <code>Level</code>. The common use for
 * this class is to connect to the <code>Lobby</code>, though in practice
 * this class may be used to move between any games.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameConnector implements Connector
{

    // the game at one end of the connection
    private GLOReference<? extends Game> connectedGame;

    // the level at the other end of the connection
    private GLOReference<? extends Level> connectedLevel;

    // the position on the level where we connect
    private int startX;
    private int startY;

    /**
     * Creates an instance of <code>GameConnector</code>.
     *
     * @param gameRef the game this connects to
     * @param levelRef the level this connects to
     * @param startX the x-coodinate on the level
     * @param startY the y-coodinate on the level
     */
    public GameConnector(GLOReference<? extends Game> gameRef,
                         GLOReference<? extends Level> levelRef,
                         int startX, int startY) {
        this.connectedGame = gameRef;
        this.connectedLevel = levelRef;

        this.startX = startX;
        this.startY = startY;
    }
    
    /**
     * Transitions the given character to the game if they're in the level,
     * and to the level if they're in the game.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(GLOReference<? extends CharacterManager>
                                  mgrRef) {
        SimTask task = SimTask.getCurrent();
        CharacterManager mgr = mgrRef.peek(task);
        GLOReference<? extends Level> levelRef = mgr.getCurrentLevel();

        // see what state the character is in, which tells us which direction
        // they're going in
        if (levelRef == null) {
            // they're not currently on a level, which means that they're
            // not yet playing a game, so move them in
            connectedLevel.get(task).addCharacter(mgrRef, startX, startY);
        } else {
            // they're leaving the game for the lobby...only players can
            // move into the lobby, so make sure there are no AIs trying
            // to sneak through
            if (! (mgr instanceof PlayerCharacterManager))
                return false;

            // FIXME: should this be queued?
            Player player = ((PlayerCharacterManager)mgr).
                getPlayerRef().get(task);
            player.moveToGame(connectedGame);
        }

        return true;
    }

}
