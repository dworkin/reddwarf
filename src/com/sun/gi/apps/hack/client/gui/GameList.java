
/*
 * GameList.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 9:20:13 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.LobbyListener;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import javax.swing.ListModel;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
class GameList implements ListModel, LobbyListener
{

    //
    private ArrayList<GameDetail> data;

    //
    private HashSet<ListDataListener> listeners;

    //
    private LobbyPanel lobbyPanel;

    /**
     *
     */
    public GameList(LobbyPanel lobbyPanel) {
        data = new ArrayList<GameDetail>();
        listeners = new HashSet<ListDataListener>();

        this.lobbyPanel = lobbyPanel;
    }

    /**
     *
     */
    public void clearList() {
        data.clear();
        notifyChange();
    }

    /**
     *
     */
    public void gameAdded(String game) {
        data.add(new GameDetail(game, 0));
        notifyChange();
    }

    /**
     *
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
     *
     */
    public void playerCountUpdated(int count) {
        lobbyPanel.updateLobbyCount(count);
    }

    /**
     *
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
     *
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        lobbyPanel.setCharacters(characters);
    }

    private void notifyChange() {
        ListDataEvent event =
            new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED,
                              0, data.size() - 1);
        for (ListDataListener listener : listeners)
            listener.contentsChanged(event);
    }

    public void addListDataListener(ListDataListener l) {
        listeners.add(l);
    }

    public Object getElementAt(int index) {
        return data.get(index);
    }

    public String getNameAt(int index) {
        return data.get(index).name;
    }

    public int getSize() {
        return data.size();
    }

    public void removeListDataListener(ListDataListener l) {
        listeners.remove(l);
    }

    /**
     *
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
