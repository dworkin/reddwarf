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

import com.sun.sgs.example.hack.client.CreatorListener;
import com.sun.sgs.example.hack.client.CreatorManager;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;


/**
 * This is a panel used to interact with the player's charcaters.
 */
public class CreatorPanel extends JPanel
    implements CreatorListener, ActionListener
{

    private static final long serialVersionUID = 1;

    // the manager used to send messages to the creator
    private CreatorManager creatorManager;

    // the popup with the list of charcater classes
    JComboBox characterClassPopup;

    // the names of the charcter classes
    // FIXME: hard-coded for now, so we just include a few
    private static final String [] classNames = { "Barbarian",
                                                  "Mexican Wrestler",
                                                  "Priest", "Theif",
                                                  "Warior", "Wizzard" };

    // the identifiers for the character classes
    // FIXME: hard-coded for now
    private static final int [] classIdentifiers = { 47, 3, 43, 44, 41, 42 };

    // the panel that displays character details
    private PlayerInfoPanel playerInfoPanel;

    // the entry field for the character name
    private JTextField nameField;

    /**
     * Creates an instance of <code>CreatorPanel</code>.
     *
     * @param creatorManager the manager used to interact with the creator
     */
    public CreatorPanel(CreatorManager creatorManager) {
        this.creatorManager = creatorManager;

        creatorManager.addCreatorListener(this);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        nameField = new JTextField(20);
        JPanel namePanel = new JPanel();
        namePanel.add(new JLabel("Name: "));
        namePanel.add(nameField);
        add(namePanel);

        characterClassPopup = new JComboBox(classNames);
        JPanel classPanel = new JPanel();
        classPanel.add(new JLabel("Class: "));
        classPanel.add(characterClassPopup);
        add(classPanel);

        playerInfoPanel = new PlayerInfoPanel();
        add(playerInfoPanel);

        JButton rollButton = new JButton("Re-roll stats");
        rollButton.addActionListener(this);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);
        JButton createButton = new JButton("Create");
        createButton.addActionListener(this);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(rollButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(createButton);
        add(buttonPanel);
    }

    /**
     * Called when an item in the character class popup is selected, or when
     * one of the buttons is pressed.
     *
     * @param e detail of the selection change
     */
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JComboBox) {
            // get a reference to the popup widget and get the stats
            JComboBox cb = (JComboBox)(e.getSource());
            int index = cb.getSelectedIndex();
            
            if (index > 0) {
                // clear stats
                playerInfoPanel.clearCharacter();
                creatorManager.rollForStats(classIdentifiers[index]);
            }
        } else {
            // see which button got hit
            String cmd = e.getActionCommand();

            if (cmd.equals("Create")) {
                // create the character
                creatorManager.createCurrentCharacter(nameField.getText());
            } else if (cmd.equals("Cancel")) {
                // cancel character creation
                creatorManager.cancelCreation();
            } else {
                // get new stats
                int index = characterClassPopup.getSelectedIndex();
                creatorManager.rollForStats(classIdentifiers[index]);
            }
        }
    }

    /**
     * Notifies the listener of new character statistics.
     *
     * @param id the character's identifier
     * @param stats the new statistics
     */
    public void changeStatistics(int id, CharacterStats stats) {
        playerInfoPanel.setCharacter(id, stats);
    }

}
