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
 * CreatorPanel.java
 *
 * Created by: seth proctor (stp)
 * Created on: Tue Mar 21, 2006	 1:04:39 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.CreatorListener;
import com.sun.gi.apps.hack.client.CreatorManager;

import com.sun.gi.apps.hack.share.CharacterStats;

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
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class CreatorPanel extends JPanel
    implements CreatorListener, ActionListener
{

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
