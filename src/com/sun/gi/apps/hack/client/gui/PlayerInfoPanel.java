
/*
 * PlayerInfoPanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 6:50:16 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.PlayerListener;

import com.sun.gi.apps.hack.share.CharacterStats;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
class PlayerInfoPanel extends JPanel implements PlayerListener
{

    //
    private JLabel nameLabel;
    private JLabel hpLabel;
    private JLabel strengthLabel;
    private JLabel intelligenceLabel;
    private JLabel dexterityLabel;
    private JLabel wisdomLabel;
    private JLabel constitutionLabel;
    private JLabel charismaLabel;
    

    /**
     *
     */
    public PlayerInfoPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        nameLabel = new JLabel();
        hpLabel = new JLabel();
        strengthLabel = new JLabel();
        intelligenceLabel = new JLabel();
        dexterityLabel = new JLabel();
        wisdomLabel = new JLabel();
        constitutionLabel = new JLabel();
        charismaLabel = new JLabel();
        
        setLabels("", 0, 0, 0, 0, 0, 0, 0, 0);

        add(nameLabel);
        add(hpLabel);
        add(strengthLabel);
        add(intelligenceLabel);
        add(dexterityLabel);
        add(wisdomLabel);
        add(constitutionLabel);
        add(charismaLabel);

        //setSize(50, 150);
    }

    /**
     *
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
     *
     */
    public void setCharacter(int id, CharacterStats stats) {
        setLabels(stats.getName(), stats.getStrength(),
                  stats.getIntelligence(), stats.getDexterity(),
                  stats.getWisdom(), stats.getConstitution(),
                  stats.getCharisma(), stats.getHitPoints(),
                  stats.getMaxHitPoints());
    }

    /**
     *
     */
    public void updateCharacter(/*FIXME: define this type*/) {

    }

}
