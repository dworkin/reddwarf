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
 * This panel is used by the lobby interface to display character details
 * and allow the player to choose which character they want to play when
 * they join a game.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class CharacterPanel extends JPanel implements ActionListener
{

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
