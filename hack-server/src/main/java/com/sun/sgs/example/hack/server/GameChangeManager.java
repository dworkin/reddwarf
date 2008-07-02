/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.Task;

import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.io.Serializable;

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
 */
public class GameChangeManager implements Task, ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * The identifier for the single manager.
     */
    public static final String IDENTIFIER = "GameChangeManager";

    /**
     * The default number of milliseconds between update calls.
     */
    public static final int CHANGE_MANAGER_FREQUENCY = 4000;

    // the set of listeners
    private HashSet<ManagedReference<GameChangeListener>> listeners;

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
        listeners = new HashSet<ManagedReference<GameChangeListener>>();
        addedGames = new HashSet<String>();
        removedGames = new HashSet<String>();
        updateMap = new HashMap<String,GameMembershipDetail>();
    }

    /**
     * Provides access to the single instance of
     * <code>GameChangeManager</code>. If a manager hasn't already
     * been created, then a new instance is created and added as a
     * registered <code>ManagedObject</code>. If the manager already
     * exists then nothing new is created.
     *
     * <p>
     *
     * See the comment in <code>Lobby.getInstance</code> for more about the
     * pattern used by these <code>getInstance</code> methods.
     *
     * @return a reference to the singleton
     *         <code>GameChangeManager</code>
     */
    public static GameChangeManager getInstance() {
        DataManager dataManager = AppContext.getDataManager();
        
        // try to get an existing reference
        GameChangeManager gcm = null;
        try {
            gcm = (GameChangeManager) dataManager.getBinding(IDENTIFIER);
        } catch (NameNotBoundException e) {
            gcm = new GameChangeManager();
            dataManager.setBinding(IDENTIFIER, gcm);
        }

        return gcm;
    }

    /**
     * Adds a listener to this manager. All listeners are called back at
     * regular intervals if there are any changes to report.
     *
     * @param listener the listener to add
     */
    public void addGameChangeListener(GameChangeListener listener) {
        DataManager dataManager = AppContext.getDataManager();
        listeners.add(dataManager.createReference(listener));
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
     */
    public void run() throws Exception {
        // for each notice type, see if we have anything to report, and if
        // we do send to all the listeners ... once we're done, clear the
        // collection

        // send the game removed notice
        if (removedGames.size() > 0) {
            for (ManagedReference<GameChangeListener> listenerRef : listeners)
                listenerRef.getForUpdate().gameRemoved(removedGames);
            removedGames.clear();
        }

        // send the game added notice
        if (addedGames.size() > 0) {
            for (ManagedReference<GameChangeListener> listenerRef : listeners)
                listenerRef.getForUpdate().gameAdded(addedGames);
            addedGames.clear();
        }

        // send the updated membership detail
        if (updateMap.size() > 0) {
            Collection<GameMembershipDetail> details = updateMap.values();
            for (ManagedReference<GameChangeListener> listenerRef : listeners)
                listenerRef.getForUpdate().membershipChanged(details);
            updateMap.clear();
        }
    }

}
