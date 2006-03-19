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

package com.sun.gi.apps.battleboard.client.swing;

import java.awt.*;
import java.awt.geom.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.*;
import javax.swing.border.*;

public class BoardSet extends JPanel implements Zapper
{
    private DisplayBoard[] boards;
    private Rectangle src;
    private ArrayList<Animation> animations = new ArrayList<Animation>();
    private Timer timer = new Timer();
    
    private Image[] explosion;
    
    public BoardSet() {
	List<Image> images = new LinkedList<Image>();

	int i = 0;
	for (;;) {
            URL url = getClass().getResource("imgs/exp" + (i++ + 1) + ".png");
	    if (url == null) {
		break;
	    }
            ImageIcon icn = new ImageIcon(url);
	    images.add(icn.getImage());
        }

        Image[] tmpExplosion = new Image[images.size()];
	explosion = images.toArray(tmpExplosion);
    }
    
    public void setBoards(DisplayBoard[] boards) {
        this.boards = boards;
        removeAll();
        // try to keep width less than 800 pixels wide
        int wid = 0;
        for (int i = 0; i < boards.length; i++) {
            wid += boards[i].getPreferredSize().width;
        }
        int h = (int)Math.ceil((float)wid/700f);
        int w = (int)Math.ceil((float)boards.length/(float)h);
        setLayout(new GridLayout(h, w, 20, 20));
        for (int i = 0; i < boards.length; i++) {
            add(boards[i]);
        }
        setBackground(Color.black);
    }

    public void paintComponent(Graphics g) {
        g.setColor(Color.black);
        g.fillRect(0, 0, getWidth(), getHeight());
    }
    
    public void paint(Graphics g) {
        super.paint(g);
        Rectangle dirty = null;
        for (Iterator<Animation> it = animations.iterator(); it.hasNext();) {
            Animation anim = it.next();
            Rectangle r = anim.paintNext(g);
            if (r == null) {
                it.remove();
            } else {
                if (dirty == null) {
                    dirty = new Rectangle(r);
                } else {
                    dirty.add(r);
                }
            }
        }
        if (dirty != null) {
            final Rectangle smudge = new Rectangle(dirty);
            timer.schedule(new TimerTask() {
                public void run() {
                    repaint(smudge);
                }
            }, 40);
        }        
    }
    
    public void zap(int x, int y, boolean fireball) {
        if (fireball) {
            animations.add(new Animation(x - 15, y - 25, explosion));
            repaint(x - 15, y - 25, 35, 50);
        }
    }
    
    public void setSource(Rectangle r) {
        src = r;
    }
    
    class Animation {
        int x;
        int y;
        Image frames[];
        int frame;
        
        public Animation(int x, int y, Image frames[]) {
            this.x = x;
            this.y = y;
            this.frames = frames;
        }
        
        public Rectangle paintNext(Graphics g) {
            g.drawImage(frames[frame], x, y, null);
            frame++;
            if (frame >= frames.length) {
                return null;
            } else {
                return new Rectangle(x, y, frames[frame].getWidth(null),
                                    frames[frame].getHeight(null));
            }
        }
    }
}
