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


package com.sun.gi.apps.mcs.matchmaker.client.test;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import com.sun.gi.apps.mcs.matchmaker.client.GameDescriptor;

/**
 * 
 * <p>Title: ChannelRoomPanel</p>
 * 
 * <p>Description: Common UI superclass for displaying the details of a 
 * channel of communication.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class ChannelRoomPanel extends JPanel {
	
	
	private HashMap<String, String> userMap;
	
	private JLabel name;
	private JLabel description;
	private JLabel numUserLabel;
	private JCheckBox isPasswordProtected;
	private int numUsers = 0;
	private int maxUsers = 0;
	private MatchMakerClientTestUI parentUI;
	private String type;
	private JPanel centerPanel;
	
	public ChannelRoomPanel(String type) {
		super(new BorderLayout());
		
		this.type = type;
		
		userMap = new HashMap<String, String>();
		
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Current " + type + " :"));
        topPanel.add(name = new JLabel());
        topPanel.add(description = new JLabel());
		
        centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        centerPanel.add(numUserLabel = new JLabel("Users: 0/?"));
        centerPanel.add(isPasswordProtected = new JCheckBox(
        		"Password Protected"));
        isPasswordProtected.setEnabled(false);
        
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
	}
	
	public void setParentUI(MatchMakerClientTestUI parentUI) {
		if (this.parentUI == null) {
			this.parentUI = parentUI;
		}
	}
	
	public void addToCenter(JComponent c) {
		centerPanel.add(c);
	}
	
    private void updateNumUsers() {
        numUserLabel.setText("Users: " + numUsers + "/" + maxUsers);
    }
	
	protected void reset() {
		userMap.clear();
    	name.setText("");
    	description.setText("");
    	isPasswordProtected.setSelected(false);
    	numUsers = 0;
    	maxUsers = 0;
    	updateNumUsers();
	}
	
    protected byte[] lookupUserID(String username) {
    	if (username != null) {
            Iterator<String> iterator = userMap.keySet().iterator();
            while (iterator.hasNext()) {
                String curID = iterator.next();
                if (username.equals(userMap.get(curID))) {
                    return stringToByteArray(curID);
                }
            }
    	}
        return new byte[0];
    }
    
    public void receiveText(byte[] from, String text, boolean wasPrivate) {
        String userName = getUserName(from);
        parentUI.receiveServerMessage(getPrefix() + userName + "" + 
        		(wasPrivate ? " (privately)" : "") + ": " + text);

    }
    
    protected void setDetails(String nameStr, String desc, boolean passProtected, int maxUsers) {
    	this.name.setText(nameStr);
    	this.description.setText(desc);
    	isPasswordProtected.setSelected(passProtected);
    	this.maxUsers = maxUsers; 
    	updateNumUsers();
    }
    
    
    private byte[] stringToByteArray(String str) {
        StringTokenizer tokenizer = new StringTokenizer(str, " ");
        byte[] array = new byte[tokenizer.countTokens()];
        int index = 0;
        while (tokenizer.hasMoreTokens()) {
            array[index] = Byte.parseByte(tokenizer.nextToken());
            index++;
        }
        return array;
    }
    
    /**
     * Convert a byte array to a String by stringing the contents
     * together. Makes for easy hashing.
     * 
     * @param array
     * @return
     */
    private String byteArrayToString(byte[] array) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < array.length; i++) {
            buffer.append(array[i] + " ");
        }
        return buffer.toString();
    }
    
    public void playerEntered(byte[] player, String userName) {
        userMap.put(byteArrayToString(player), userName);
        numUsers++;
        updateNumUsers();
        parentUI.receiveServerMessage(getPrefix() + userName + " Entered " +
                type + " " + name.getText());
    }
    
    protected String removePlayer(byte[] player) {
        numUsers--;
        updateNumUsers();
        String userName = userMap.remove(byteArrayToString(player));
        parentUI.receiveServerMessage(getPrefix() + userName + " Left " +
                type + " " + name.getText());
        return userName;
    }
    
    protected void playerBootedFromGame(byte[] booter, byte[] bootee, 
			boolean isBanned) {

    	parentUI.receiveServerMessage(getPrefix() + getUserName(booter) + 
    						" has booted "  + (isBanned ? "(and banned) " : "") + 
		getUserName(bootee) + " from the game");

    }
    
    protected String getUserName(byte[] player) {
    	return userMap.get(byteArrayToString(player));
    }
    
    private String getPrefix() {
    	return "<" + type + ">: ";
    }
    
    public void gameUpdated(GameDescriptor game) {
    	parentUI.receiveServerMessage(getPrefix() + game.getName() + 
    			" has been updated.");
    }
    

}
