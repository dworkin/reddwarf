
/*
 * LobbyPanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 8:44:37 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.LobbyManager;

import com.sun.gi.apps.hack.share.CharacterStats;

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
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyPanel extends JPanel
    implements ActionListener, ListSelectionListener
{

    //
    private GameList list;

    //
    private LobbyManager manager;

    //
    private CharacterPanel characterPanel;

    //
    private JButton joinButton;

    //
    private JLabel countLabel;

    //
    private int currentSelection = -1;

    /**
     *
     */
    public LobbyPanel(LobbyManager lobbyManager) {
        super(new BorderLayout(4, 4));

        this.manager = lobbyManager;

        list = new GameList(this);
        lobbyManager.addLobbyListener(list);

        JList jlist = new JList(list);
        jlist.setVisibleRowCount(12);
        jlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jlist.addListSelectionListener(this);

        characterPanel = new CharacterPanel();

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
     *
     */
    public void clearList() {
        list.clearList();
        updateLobbyCount(0);
    }

    /**
     *
     */
    public void actionPerformed(ActionEvent ae) {
        manager.action(list.getNameAt(currentSelection),
                       characterPanel.getCharacterName());
    }

    /**
     *
     */
    public void valueChanged(ListSelectionEvent e) {
        if (! e.getValueIsAdjusting()) {
            //
            JList list = (JList)(e.getSource());

            //
            currentSelection = list.getSelectedIndex();
            joinButton.setEnabled(! list.isSelectionEmpty());
        }
    }

    /**
     *
     */
    public void updateLobbyCount(int count) {
        countLabel.setText("Players in Lobby: " + count);
    }

    /**
     *
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        characterPanel.setCharacters(characters);
    }

}
