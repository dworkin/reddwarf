/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.PlayerListener;

import com.sun.sgs.example.hack.share.CharacterStats;

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
 */
class PlayerInfoPanel extends JPanel implements PlayerListener
{

    private static final long serialVersionUID = 1;

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
    public void updateCharacter() {

    }

}
