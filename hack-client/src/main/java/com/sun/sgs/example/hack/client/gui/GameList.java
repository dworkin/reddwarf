/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.LobbyListener;

import com.sun.sgs.example.hack.share.CharacterStats;


import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;


/**
 * A model used to back a GUI list element. This model manages all the
 * games and associated detail available from the lobby.
 */
class GameList implements ListModel, LobbyListener
{

    // the backing list data
    private Map<String,Integer> gameToPlayerCount;

    // the listeners who are listening for list changes
    private Set<ListDataListener> listeners;

    // the panel that owns the list
    private LobbyPanel lobbyPanel;

    /**
     * Creates an instance of <code>GameList</code>.
     *
     * @param lobbyPanel the panel that shows the list this model is backing
     */
    public GameList(LobbyPanel lobbyPanel) {
        gameToPlayerCount = new LinkedHashMap<String,Integer>();
        listeners = new HashSet<ListDataListener>();

        this.lobbyPanel = lobbyPanel;
    }

    /**
     * Clears the contents of this list.
     */
    public void clearList() {
        gameToPlayerCount.clear();
        notifyChange();
    }

    /**
     * Adds a game to the list. The membership count for the game starts at
     * zero.
     *
     * @param game the name of the game
     */
    public void gameAdded(String game) {
        gameToPlayerCount.put(game, 0);
        notifyChange();
    }

    /**
     * Removes a game from the list.
     *
     * @param game the name of the game
     */
    public void gameRemoved(String game) {
	if (gameToPlayerCount.remove(game) != null)
	    notifyChange();
    }

    /**
     * Updates the membership count for the lobby.
     *
     * @param count the new membership count
     */
    public void playerCountUpdated(int count) {
        lobbyPanel.updateLobbyCount(count);
    }

    /**
     * Updates the membership count for a specific game.
     *
     * @param game the game being updated
     * @param count the new membership count
     */
    public void playerCountUpdated(String game, int count) {
	gameToPlayerCount.put(game, count);
        notifyChange();
    }

    /**
     * Sets the characters available for the player to use.
     *
     * @param characters the character details
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        lobbyPanel.setCharacters(characters);
    }

    /**
     * A private helper that notifies all listeners that the list has changed.
     */
    private void notifyChange() {
        // Note that this could be done much more cleanly, by notifying the
        // listeners about only the items that changed. If this game started
        // hosting many dungeons, then it would be more important, but given
        // the current scale of the game the current mechanism is easier.
        final ListDataEvent event =
            new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED,
                              0, gameToPlayerCount.size() - 1);
        
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (ListDataListener listener : listeners)
                    listener.contentsChanged(event);
            }
        });
    }

    /**
     * Adds a listener that should be notified when the list changes.
     *
     * @param l the listener to add
     */
    public void addListDataListener(ListDataListener l) {
        listeners.add(l);
    }

    /**
     * Removes a listener from the set of registered listeners.
     *
     * @param l the listener to remove
     */
    public void removeListDataListener(ListDataListener l) {
        listeners.remove(l);
    }

    /**
     * Returns the item at the given index.
     *
     * @param index the list index
     */
    public Object getElementAt(int index) {
	int i = 0;
	for (Map.Entry<String,Integer> e : gameToPlayerCount.entrySet()) {
	    if (i == index)
		return new GameDetail(e.getKey(), e.getValue());
	}
	return null;
    }

    /**
     * Returns the game name at the given index. This is needed because the
     * element at each index is the game name and the player count.
     *
     * @param index the list index
     */
    public String getNameAt(int index) {
	int i = 0;
	for (Map.Entry<String,Integer> e : gameToPlayerCount.entrySet()) {
	    if (i == index)
		return e.getKey();
	}
        return null;
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the list size
     */
    public int getSize() {
        return gameToPlayerCount.size();
    }

    /**
     * A private inner class that maintains the name and player count at
     * each index in the list.
     */
    class GameDetail {
        public String name;
        public int count;
        public GameDetail(String name, int count) {
            this.name = name;
            this.count = count;
        }
        public String toString() {
            return name + " (" + count + ")";
        }
    }

}
