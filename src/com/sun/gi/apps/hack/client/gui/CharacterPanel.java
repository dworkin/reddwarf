
/*
 * CharacterPanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar  9, 2006	 1:31:11 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.awt.BorderLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.Collection;
import java.util.TreeMap;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class CharacterPanel extends JPanel implements ActionListener
{

    //
    private PlayerInfoPanel playerInfoPanel;

    //
    private JComboBox popup;

    //
    private TreeMap<String,CharacterStats> characters;

    //
    private CharacterStats nullStats;

    //
    private String currentCharacter;

    /**
     *
     */
    public CharacterPanel() {
        super(new BorderLayout(4, 4));

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
     *
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        this.characters = new TreeMap<String,CharacterStats>();
        popup.removeAllItems();

        for (CharacterStats stats : characters)
            this.characters.put(stats.getName(), stats);

        for (String characterName : this.characters.keySet())
            popup.addItem(characterName);
    }

    /**
     *
     */
    public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)(e.getSource());
        CharacterStats stats = characters.get((String)cb.getSelectedItem());

        if (stats != null) {
            playerInfoPanel.setCharacter(-1, stats);
            currentCharacter = stats.getName();
        } else {
            playerInfoPanel.setCharacter(-1, nullStats);
            currentCharacter = "";
        }
    }

    /**
     *
     */
    public String getCharacterName() {
        return currentCharacter;
    }

}
