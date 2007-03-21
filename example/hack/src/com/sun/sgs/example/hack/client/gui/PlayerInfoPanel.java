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

import com.sun.gi.apps.hack.client.PlayerListener;

import com.sun.gi.apps.hack.share.CharacterStats;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;


/**
 * This panel displays details about one player character.
 * <p>
 * Note that this is a fairly naive, simple implementation. This should be
 * updated to handle changes more effectively, and to display things more
 * aesthetically. For now, this class is used to centralize this
 * functionality so it's easy to update.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class PlayerInfoPanel extends JPanel implements PlayerListener
{

    // the labels used to display the character detail
    private JLabel nameLabel;
    private JLabel hpLabel;
    private JLabel strengthLabel;
    private JLabel intelligenceLabel;
    private JLabel dexterityLabel;
    private JLabel wisdomLabel;
    private JLabel constitutionLabel;
    private JLabel charismaLabel;

    /**
     * Creates an instance of <code>PlayerInfoPanel</code>.
     */
    public PlayerInfoPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        // create all the labels
        nameLabel = new JLabel();
        hpLabel = new JLabel();
        strengthLabel = new JLabel();
        intelligenceLabel = new JLabel();
        dexterityLabel = new JLabel();
        wisdomLabel = new JLabel();
        constitutionLabel = new JLabel();
        charismaLabel = new JLabel();
        
        // initialize the character stats
        clearCharacter();

        // add all the labels to the GUI
        add(nameLabel);
        add(hpLabel);
        add(strengthLabel);
        add(intelligenceLabel);
        add(dexterityLabel);
        add(wisdomLabel);
        add(constitutionLabel);
        add(charismaLabel);
    }

    /**
     * Private helper used to set the text of all labels.
     */
    private void setLabels(String name, int strength, int intelligence,
                           int dexterity, int wisdom, int constitution,
                           int charisma, int hitPoints, int maxHitPoints) {
        nameLabel.setText("Name: " + name);
        hpLabel.setText("HP: " + hitPoints + " of " + maxHitPoints);
        strengthLabel.setText("STR: " + strength);
        intelligenceLabel.setText("INT: " + intelligence);
        dexterityLabel.setText("DEX: " + dexterity);
        wisdomLabel.setText("WIS: " + wisdom);
        constitutionLabel.setText("CON: " + constitution);
        charismaLabel.setText("CHR: " + charisma);
    }

    /**
     * Sets which character this panel is currently displaying.
     *
     * @param id the sprite id for the character
     * @param stats the statistics for the character
     */
    public void setCharacter(int id, CharacterStats stats) {
        setLabels(stats.getName(), stats.getStrength(),
                  stats.getIntelligence(), stats.getDexterity(),
                  stats.getWisdom(), stats.getConstitution(),
                  stats.getCharisma(), stats.getHitPoints(),
                  stats.getMaxHitPoints());
    }

    /**
     * Clears the character statistics.
     */
    public void clearCharacter() {
        setLabels("", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Updates the current character detail. Note that this may be removed,
     * since it isn't currently being used.
     */
    public void updateCharacter(/*FIXME: define this type*/) {

    }

}
