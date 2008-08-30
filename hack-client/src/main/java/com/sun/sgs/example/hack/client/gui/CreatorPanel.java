/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.CreatorListener;
import com.sun.sgs.example.hack.client.CreatorManager;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.CreatureInfo;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;

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

    private static final CreatureType[] playableCharacterClasses =
    {
	CreatureType.BARBARIAN,
	CreatureType.PRIEST,
	CreatureType.THIEF,
	CreatureType.ARCHAEOLOGIST,
	CreatureType.WARRIOR,
	CreatureType.VALKYRIE,
	CreatureType.GUARD,
	CreatureType.TOURIST
    };

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

	String[] classNames = new String[playableCharacterClasses.length];
	for (int i = 0; i < classNames.length; ++i)
	    classNames[i] = 
		CreatureInfo.getPrintableName(playableCharacterClasses[i]);

        characterClassPopup = new JComboBox(classNames);
	characterClassPopup.addActionListener(this);
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
                creatorManager.rollForStats(playableCharacterClasses[index]);
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
                creatorManager.rollForStats(playableCharacterClasses[index]);
            }
        }
    }

    /**
     * Notifies the listener of new character statistics.
     *
     * @param id the character's identifier
     * @param stats the new statistics
     */
    public void changeStatistics(CreatureType characterClassType,
				 CharacterStats stats) {
        playerInfoPanel.setCharacter(characterClassType, stats);
    }

}
