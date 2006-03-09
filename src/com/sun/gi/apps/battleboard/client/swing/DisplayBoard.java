/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.apps.battleboard.client.swing;

import com.sun.gi.apps.battleboard.BattleBoard;
import com.sun.gi.apps.battleboard.BoardListener;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.geom.*;
import java.awt.font.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;


/**
 * The board display looks like this:
 * <pre>
 *    PLAYER NAME (2/2)   &lt;- 20 px height
 *     o o o o o o o o    &lt;- 10 px height
 *     o o o o o o o o
 *     o o o o o o o o
 *     o o o o o o o o
 *     o o o o o o o o
 *     o o o o o o o o
 *     o o o o o o o o
 * </pre>
 *  If active, there is a bright green border around the grid.  This
 * border should "zap" to the location of a change.
 *
 */
public class DisplayBoard extends JPanel implements BoardListener
{
    private static final int GRIDSIZE = 16;
    private int GRIDXOFFSET = 0;
    private static final int GRIDYOFFSET = 23;
    private static final Color BGCOLOR = new Color(60, 50, 50);
    private static final Color NAMECOLOR = new Color(255, 255, 255);
    private static final Color VACANTCOLOR = new Color(70, 70, 70);
    private static final Color CITYCOLOR = new Color(0, 255, 0);
    private static final Color UNKNOWNCOLOR = new Color(0, 0, 0);
    private static final Color NEARCOLOR = new Color(180, 180, 0);
    private static final Color MISSCOLOR = new Color(60, 50, 50);
    private static final Color HITCOLOR = new Color(255, 0, 0);
    private static final Color DONECOLOR = new Color(60, 60, 60);
    
    private static final Font TITLEFONT = new Font("Sans-serif",
                                                   Font.BOLD, 14);
    private static final FontRenderContext frc =
        new FontRenderContext(new AffineTransform(), true, true);
    
    /** Mapping from PositionValue to Color */
    private static HashMap<BattleBoard.PositionValue,Color> colors;
    
    /** The board */
    private BattleBoard board;
    
    /** The Zapper */
    private Zapper zapper;
    
    /** title line (name and cities remaining) */
    private String title;
    
    private boolean active;
    
    private boolean done = false;
    
    private Color values[];
    private ArrayList<Animation> animations = new ArrayList<Animation>();
    
    private ArrayList<MoveListener> moveListeners =
        new ArrayList<MoveListener>();

    static {
        colors = new HashMap<BattleBoard.PositionValue,Color>();
        colors.put(BattleBoard.PositionValue.VACANT, VACANTCOLOR);
        colors.put(BattleBoard.PositionValue.CITY, CITYCOLOR);
        colors.put(BattleBoard.PositionValue.UNKNOWN, UNKNOWNCOLOR);
        colors.put(BattleBoard.PositionValue.NEAR, NEARCOLOR);
        colors.put(BattleBoard.PositionValue.MISS, MISSCOLOR);
        colors.put(BattleBoard.PositionValue.HIT, HITCOLOR);
    }
    
    /**
     * Creates a new DisplayBoard.
     */
    public DisplayBoard(BattleBoard board, Zapper zapper) {
        this.board = board;
        this.zapper = zapper;
        buildTitle();
        int bwid = board.getWidth();
        int bhgt = board.getHeight();
        values = new Color[bwid*bhgt];
        for (int y = 0; y<bhgt; y++) {
            for (int x = 0; x<bwid; x++) {
                values[x+y*bwid] = colors.get(board.getBoardPosition(x, y));
            }
        }
        int twid = (int)TITLEFONT.getStringBounds(title, frc).getWidth()+3;
        int gwid = board.getWidth()*GRIDSIZE;
        int wid = gwid > twid ? gwid : twid;
        wid += 6;
        int hgt = board.getHeight()*GRIDSIZE + GRIDYOFFSET + 6;
        GRIDXOFFSET = (wid-gwid)/2;
        setPreferredSize(new Dimension(wid, hgt));
        board.addBoardListener(this);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                doClick(evt.getX(), evt.getY());
            }
        });
    }
    
    private void buildTitle() {
        this.title = board.getPlayerName() + " (" + board.getSurvivingCities() +
                    "/" + board.getStartCities() + ")";
    }

    /**
     * Called when a location on the board changes.
     */
    public void boardChanged(BattleBoard board, int x, int y) {
        Point p = getLocation();
        int cornerx = x*GRIDSIZE + GRIDXOFFSET;
        int cornery = y*GRIDSIZE + GRIDYOFFSET;
        BattleBoard.PositionValue pv = board.getBoardPosition(x, y);
        if (pv == BattleBoard.PositionValue.HIT) {
            // wait 150 ms, then set the color.
            // repaint will happen as the zapper animates the explosion.
            final int pos = x+y*board.getWidth();
            final Color color = colors.get(pv);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ie) {}
                    values[pos] = color;
                }
            }).start();
        } else {
            animations.add(new Animation(x, y, values[x+y*board.getWidth()], 
                                     colors.get(pv)));
        }
        values[x+y*board.getWidth()] = null;
        if (zapper != null) {
            zapper.zap(cornerx + GRIDSIZE/2 + p.x,
                       cornery + GRIDSIZE/2 + p.y,
                       pv == BattleBoard.PositionValue.HIT);
        }
        // what did it turn into?
        if (pv == BattleBoard.PositionValue.HIT) {
            buildTitle();
            repaint();
        } else {
            repaint(cornerx, cornery, GRIDSIZE, GRIDSIZE);
        }
    }

    public void paint(Graphics g1) {
        Graphics2D g = (Graphics2D)g1;
        if (active) {
            g.setColor(Color.cyan);
        } else {
            g.setColor(Color.black);
        }
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(BGCOLOR);
        g.fillRect(3, 3, getWidth()-6, getHeight()-6);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        GRIDXOFFSET = (getWidth() - board.getWidth()*GRIDSIZE)/2;
        if (done) {
            g.setColor(DONECOLOR);
        } else {
            g.setColor(NAMECOLOR);
        }
        g.setFont(TITLEFONT);
        int wid = (int)TITLEFONT.getStringBounds(title, frc).getWidth();
        g.drawString(title, (getWidth()-wid)/2, GRIDYOFFSET-4);
        int bwid = board.getWidth();
        for (int x = 0; x < bwid; x++) {
            for (int y = 0; y<board.getHeight(); y++) {
                if (!done) {
                    Color c = values[x+y*bwid];
                    if (c == null) {
                        continue;
                    }
                    g.setColor(c);
                }
                int cornerx = x*GRIDSIZE + GRIDXOFFSET;
                int cornery = y*GRIDSIZE + GRIDYOFFSET;
                g.fillOval(cornerx+1, cornery+1, GRIDSIZE-2, GRIDSIZE-2);
            }
        }
        // now draw animations
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
            repaint(dirty);
        }        
        
    }
    
    public void addMoveListener(MoveListener listener) {
        synchronized(moveListeners) {
            moveListeners.add(listener);
        }
    }
    
    public void removeMoveListener(MoveListener listener) {
        synchronized(moveListeners) {
            moveListeners.remove(listener);
        }
    }
    
    public void fireMove(String[] move) {
        synchronized(moveListeners) {
            for (MoveListener listener : moveListeners) {
                listener.moveMade(move);
            }
        }
    }
    
    public void doClick(int x, int y) {
        if (x < GRIDXOFFSET || y < GRIDYOFFSET || done) {
            return;
        }
        x = (x-GRIDXOFFSET)/GRIDSIZE;
        y = (y-GRIDYOFFSET)/GRIDSIZE;
        if (x<board.getWidth() && y<board.getHeight()) {
            String move[] = new String[3];
            move[0] = board.getPlayerName();
            move[1] = String.valueOf(x);
            move[2] = String.valueOf(y);
            fireMove(move);
        }
    }

    /**
     * Marks this board as finished.
     */
    public void endBoard() {
        this.done = true;
        setCursor(null);
        repaint();
    }
    
    /**
     */
    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            Rectangle r = getBounds();
            if (zapper != null) {
                zapper.setSource(r);
            }
        }
        repaint();
    }
    
    class Animation {
        int idx;
        int cornerx;
        int cornery;
        Color from;
        Color to;
        int frame;
        
        public Animation(int x, int y, Color from, Color to) {
            this.idx= x+y*board.getWidth();
            this.cornerx = x*GRIDSIZE + GRIDXOFFSET;
            this.cornery = y*GRIDSIZE + GRIDYOFFSET;
            this.from = from;
            this.to = to;
        }
        
        public Rectangle paintNext(Graphics g) {
            if (frame<15) {
                g.setColor(from);
            } else {
                g.setColor(to);
            }
            float cos = (float)Math.cos(frame*Math.PI/30);
            int wid = (int)Math.abs(cos*(GRIDSIZE-2));
            g.fillOval(cornerx+ (GRIDSIZE-wid)/2, cornery+1, wid, GRIDSIZE-2);
            frame++;
            if (frame>30) {
                values[idx] = to;
                return null;
            } else {
                return new Rectangle(cornerx, cornery, GRIDSIZE, GRIDSIZE);
            }
        }
    }

}
