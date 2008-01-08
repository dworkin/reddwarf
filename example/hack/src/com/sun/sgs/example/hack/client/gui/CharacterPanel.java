/*
 * Copyright 2008 Sun Microsystems, Inc.
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

import com.sun.sgs.example.hack.share.CharacterStats;

import java.awt.BorderLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.Collection;
import java.util.TreeMap;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;


/**
 * This panel is used by the lobby interface to display character details
 * and allow the player to choose which character they want to play when
 * they join a game.
 */
public class CharacterPanel extends JPanel implements ActionListener
{

    private static final long serialVersionUID = 1;

    // the panel that shows character details
    private PlayerInfoPanel playerInfoPanel;

    // the popup with the list of characters
    private JComboBox popup;

    // the mapping from character names to their stats
    private TreeMap<String,CharacterStats> characters;

    // a defeault set of empty stats, used when no characters are available
    private CharacterStats nullStats;

    // the name of the currently selected character
    private String currentCharacter;

    /**
     * Creates an instance of <code>CharacterPanel</code>.
     */
    public CharacterPanel() {
        super(new BorderLayout(4, 4));

        // initialize some null stats
        nullStats = new CharacterStats("",0,0,0,0,0,0,0,0);
        currentCharacter = "";

        playerInfoPanel = new PlayerInfoPanel();

        popup = new JComboBox();
        popup.addActionListener(this);

        JPanel popupPanel = new JPanel();
        popupPanel.add(new JLabel("Play As:"));
        popupPanel.add(popup);

        add(playerInfoPanel, BorderLayout.CENTER);
        add(popupPanel, BorderLayout.NORTH);
    }

    /**
     * Sets the available set of characters for this player.
     *
     * @param characters the set of available characters
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        // re-initialize the map and clear the popup
        this.characters = new TreeMap<String,CharacterStats>();
        popup.removeAllItems();

        // seed the map with the available characters
        for (CharacterStats stats : characters)
            this.characters.put(stats.getName(), stats);

        // add the now-sorted list of character names to the popup
        for (String characterName : this.characters.keySet())
            popup.addItem(characterName);
    }

    /**
     * Called when an item in the popup is selected.
     *
     * @param e detail of the selection change
     */
    public void actionPerformed(ActionEvent e) {
        // get a reference to the popup widget and get the stats
        JComboBox cb = (JComboBox)(e.getSource());
        CharacterStats stats = characters.get((String)cb.getSelectedItem());

        if (stats != null) {
            // we selected something valid, so track that current selection
            playerInfoPanel.setCharacter(-1, stats);
            currentCharacter = stats.getName();
        } else {
            // we de-selected, so go to the null character
            playerInfoPanel.setCharacter(-1, nullStats);
            currentCharacter = "";
        }
    }

    /**
     * Returns the name of the currently selected character.
     *
     * @return the current character's name
     */
    public String getCharacterName() {
        return currentCharacter;
    }

}
