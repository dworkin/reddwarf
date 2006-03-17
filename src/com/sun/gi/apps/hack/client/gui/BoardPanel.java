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
 * BoardPanel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Feb 16, 2006	 2:56:46 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.BoardListener;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;

import java.awt.image.BufferedImage;

import java.util.Map;

import javax.swing.JPanel;


/**
 *
 */
class BoardPanel extends JPanel implements BoardListener
{

    //
    private Map<Integer,Image> spriteMap;

    //
    private BufferedImage offscreen;

    //
    private int spriteSize = 0;

    /**
     *
     */
    public BoardPanel() {
        offscreen = new BufferedImage(50, 50,
                                      BufferedImage.TYPE_INT_RGB);
        Graphics g = offscreen.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        g.dispose();
    }

    /**
     *
     */
    public void showLoadingScreen() {
        offscreen = new BufferedImage(getWidth(), getHeight(),
                                      BufferedImage.TYPE_INT_RGB);
        Graphics g = offscreen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        g.setColor(Color.BLACK);
        g.drawString("Loading...", 20, 30);
        g.dispose();
    }

    /**
     *
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {
        this.spriteSize = spriteSize;
        this.spriteMap = spriteMap;
    }

    /**
     *
     */
    public void changeBoard(Board board) {
        int w = board.getWidth() * spriteSize;
        int h = board.getHeight() * spriteSize;

        offscreen = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        setSize(w, h);

        Graphics g = offscreen.createGraphics();
        
        if (board.isDark()) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        } else {
            for (int x = 0; x < board.getWidth(); x++)
                for (int y = 0; y < board.getHeight(); y++)
                    updateSpace(g, x, y, board.getAt(x, y));
        }

        g.dispose();
        repaint();
    }

    /**
     *
     */
    private void updateSpace(Graphics g, int x, int y, int [] ids) {
        for (int i = 0; i < ids.length; i++)
            g.drawImage(spriteMap.get(ids[i]), x * spriteSize, y * spriteSize,
                        this);
    }

    /**
     *
     */
    public void updateSpaces(BoardSpace [] spaces) {
        Graphics g = offscreen.createGraphics();

        for (int i = 0; i < spaces.length; i++)
            updateSpace(g, spaces[i].getX(), spaces[i].getY(),
                        spaces[i].getIdentifiers());

        g.dispose();
        repaint();
    }

    /**
     *
     */
    public void hearMessage(String message) {
        // FIXME: this should be painted on the board somewhere, but for
        // now we'll just print it out
        System.out.println(message);
    }

    /**
     *
     */
    public void paint(Graphics g) {
        g.drawImage(offscreen, 0, 0, this);
    }

}
