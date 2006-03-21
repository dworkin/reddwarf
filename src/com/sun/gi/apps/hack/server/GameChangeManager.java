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

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTimerListener;

import com.sun.gi.apps.hack.share.GameMembershipDetail;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;


/**
 * This class acts as an aggregator for game updates. It is called
 * when mambership counts change in any games, or when games are added
 * or removed from the app. It collects the updates, and on regular
 * intervals it sends the details to a set of listeners that it manages,
 * and then clears its update list. Updates are compressed so there is
 * only one update for each game. There is only one instance of
 * <code>GameChangeManager</code> for each game app.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameChangeManager implements SimTimerListener
{

    /**
     * The identifier for the single manager.
     */
    public static final String IDENTIFIER = "GameChangeManager";

    /**
     * The default number of milliseconds between update calls.
     */
    public static final int CHANGE_MANAGER_FREQUENCY = 4000;

    // the set of listeners
    private HashSet<GLOReference<? extends GameChangeListener>> listeners;

    // the set of added games
    private HashSet<String> addedGames;

    // the set of removed games
    private HashSet<String> removedGames;

    // the map up update events, from game name to detail
    private HashMap<String,GameMembershipDetail> updateMap;

    /**
     * Creates an instance of <code>GameChangeManager</code>. In
     * practice there should only ever be one of these, so we don't all
     * direct access to the constructor. Instead, you get access through
     * <code>getInstance</code> and that enforces the singleton.
     */
    private GameChangeManager() {
        listeners =
            new HashSet<GLOReference<? extends GameChangeListener>>();

        addedGames = new HashSet<String>();
        removedGames = new HashSet<String>();
        updateMap = new HashMap<String,GameMembershipDetail>();
    }

    /**
     * Provides access to the single instance of
     * <code>GameChangeManager</code>. If a manager hasn't already been
     * created, then a new instance is created and added as a registered
     * <code>GLO</code>. If the manager already exists then nothing new is
     * created.
     * <p>
     * See the comment in <code>Lobby.getInstance</code> for more about the
     * pattern used by these <code>getInstance</code> methods.
     *
     * @return a reference to the single <code>GameChangeManager</code>
     */
    public static GLOReference<GameChangeManager> getInstance() {
        SimTask task = SimTask.getCurrent();

        // try to get an existing reference
        GLOReference<GameChangeManager> gcmRef = task.findGLO(IDENTIFIER);

        // if we couldn't find a reference, then create it
        if (gcmRef == null) {
            gcmRef = task.createGLO(new GameChangeManager(), IDENTIFIER);

            // if doing the create returned null then someone beat us to
            // it, so get their already-registered reference
            if (gcmRef == null)
                gcmRef = task.findGLO(IDENTIFIER);
        }

        // return the reference
        return gcmRef;
    }

    /**
     * Adds a listener to this manager. All listeners are called back at
     * regular intervals if there are any changes to report.
     *
     * @param listener the listener to add
     */
    public void addGameChangeListener(GLOReference
            <? extends GameChangeListener> listener) {
        listeners.add(listener);
    }

    /**
     * Notifies the manager that a game was added to the app.
     *
     * @param game the name of the <code>Game</code>
     */
    public void notifyGameAdded(String game) {
        addedGames.add(game);

        // if this was previous removed, cancel that notification
        removedGames.remove(game);
    }

    /**
     * Notifies the manager that a game was removed from the app.
     *
     * @param game the name of the <code>Game</code>
     */
    public void notifyGameRemoved(String game) {
        removedGames.add(game);

        // if there was previous data queued about this game, remove it
        addedGames.remove(game);
        updateMap.remove(game);
    }

    /**
     * Notifies the manager that membership detail has changed in a specific
     * game.
     *
     * @param detail the update information
     */
    public void notifyMembershipChanged(GameMembershipDetail detail) {
        updateMap.put(detail.getGame(), detail);
    }

    /**
     * Called at periodic intervals by the system, this method notifies
     * all registered listeners if there have been any updates since the
     * last notification.
     *
     * @param eventID the event identifier
     */
    public void timerEvent(long eventID) {
        // for each notice type, see if we have anything to report, and if
        // we do send to all the listeners ... once we're done, clear the
        // collection
        SimTask task = SimTask.getCurrent();

        // send the game removed notice
        if (removedGames.size() > 0) {
            for (GLOReference<? extends GameChangeListener> listenerRef :
                     listeners)
                listenerRef.get(task).gameAdded(addedGames);
            removedGames.clear();
        }

        // send the game added notice
        if (addedGames.size() > 0) {
            for (GLOReference<? extends GameChangeListener> listenerRef :
                     listeners)
                listenerRef.get(task).gameAdded(addedGames);
            addedGames.clear();
        }

        // send the updated membership detail
        if (updateMap.size() > 0) {
            Collection<GameMembershipDetail> details = updateMap.values();
            for (GLOReference<? extends GameChangeListener> listenerRef :
                     listeners)
                listenerRef.get(task).membershipChanged(details);
            updateMap.clear();
        }
    }

}
