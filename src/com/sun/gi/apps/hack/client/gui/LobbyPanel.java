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
 * A panel that manages all the lobby GUI elements.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyPanel extends JPanel
    implements ActionListener, ListSelectionListener
{

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
