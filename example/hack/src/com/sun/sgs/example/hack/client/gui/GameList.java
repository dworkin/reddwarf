/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.LobbyListener;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import javax.swing.ListModel;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;


/**
 * A model used to back a GUI list element. This model manages all the
 * games and associated detail available from the lobby.
 */
class GameList implements ListModel, LobbyListener
{

    // the backing list data
    private ArrayList<GameDetail> data;

    // the listeners who are listening for list changes
    private HashSet<ListDataListener> listeners;

    // the panel that owns the list
    private LobbyPanel lobbyPanel;

    /**
     * Creates an instance of <code>GameList</code>.
     *
     * @param lobbyPanel the panel that shows the list this model is backing
     */
    public GameList(LobbyPanel lobbyPanel) {
        data = new ArrayList<GameDetail>();
        listeners = new HashSet<ListDataListener>();

        this.lobbyPanel = lobbyPanel;
    }

    /**
     * Clears the contents of this list.
     */
    public void clearList() {
        data.clear();
        notifyChange();
    }

    /**
     * Adds a game to the list. The membership count for the game starts at
     * zero.
     *
     * @param game the name of the game
     */
    public void gameAdded(String game) {
        data.add(new GameDetail(game, 0));
        notifyChange();
    }

    /**
     * Removes a game from the list.
     *
     * @param game the name of the game
     */
    public void gameRemoved(String game) {
        for (GameDetail detail : data) {
            if (detail.name.equals(game)) {
                data.remove(detail);
                break;
            }
        }

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
        for (GameDetail detail : data) {
            if (detail.name.equals(game)) {
                detail.count = count;
                break;
            }
        }

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
        ListDataEvent event =
            new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED,
                              0, data.size() - 1);
        for (ListDataListener listener : listeners)
            listener.contentsChanged(event);
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
        return data.get(index);
    }

    /**
     * Returns the game name at the given index. This is needed because the
     * element at each index is the game name and the player count.
     *
     * @param index the list index
     */
    public String getNameAt(int index) {
        return data.get(index).name;
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the list size
     */
    public int getSize() {
        return data.size();
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
