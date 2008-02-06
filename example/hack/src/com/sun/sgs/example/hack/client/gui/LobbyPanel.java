/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.example.hack.client.LobbyManager;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.awt.BorderLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.Collection;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


/**
 * A panel that manages all the lobby GUI elements.
 */
public class LobbyPanel extends JPanel
    implements ActionListener, ListSelectionListener
{

    private static final long serialVersionUID = 1;

    // the manager for the list of games that are available to play
    private GameList list;

    // the manager for talking to the lobby
    private LobbyManager manager;

    // the panel used to choose players and see their stata
    private CharacterPanel characterPanel;

    // the button you hit to join a game
    private JButton joinButton;

    // a label displaying the current lobby count
    private JLabel countLabel;

    // which game is currently selected in the list
    private int currentSelection = -1;

    /**
     * Creates an instance of <code>LobbyPanel</code>.
     *
     * @param lobbyManager the manager used to talk with the server
     */
    public LobbyPanel(LobbyManager lobbyManager) {
        super(new BorderLayout(4, 4));

        this.manager = lobbyManager;

        // create the manager for the list...
        list = new GameList(this);
        lobbyManager.addLobbyListener(list);

        // ...and create the GUI list element to render the game details
        JList jlist = new JList(list);
        jlist.setVisibleRowCount(12);
        jlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jlist.addListSelectionListener(this);

        characterPanel = new CharacterPanel();

        // start the join button as disabled, so we only turn it on when
        // some game has been selected
        joinButton = new JButton("Join");
        joinButton.addActionListener(this);
        joinButton.setEnabled(false);

        countLabel = new JLabel("Players in Lobby: 0");

        add(new JScrollPane(jlist), BorderLayout.CENTER);
        add(joinButton, BorderLayout.SOUTH);
        add(countLabel, BorderLayout.NORTH);
        add(characterPanel, BorderLayout.EAST);
    }

    /**
     * Clears the current list of games. Typically, this is done each time
     * the player returns to the lobby.
     */
    public void clearList() {
        list.clearList();
        updateLobbyCount(0);
    }

    /**
     * Called when the user clicks on the join button.
     *
     * @param ae details about the action
     */
    public void actionPerformed(ActionEvent ae) {
        manager.joinGame(list.getNameAt(currentSelection),
                         characterPanel.getCharacterName());
    }

    /**
     * Called when the selected item in the GUI list changes.
     *
     * @param e details about what is changing
     */
    public void valueChanged(ListSelectionEvent e) {
        if (! e.getValueIsAdjusting()) {
            // get a pointer to the GUI list element
            JList list = (JList)(e.getSource());

            // get the current selection, and toggle the button on or off
            // based on whether anything is selected
            currentSelection = list.getSelectedIndex();
            joinButton.setEnabled(! list.isSelectionEmpty());
        }
    }

    /**
     * Called when the lobby count changes.
     *
     * @param count the new lobby membership count
     */
    public void updateLobbyCount(int count) {
        countLabel.setText("Players in Lobby: " + count);
    }

    /**
     * Sets the characters that are available for the player to use.
     *
     * @param characters the character details
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        characterPanel.setCharacters(characters);
    }

}
