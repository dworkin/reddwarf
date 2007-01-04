package com.sun.sgs.example.battleboard.client.swing;

import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.*;

public class BoardSet extends JPanel implements Zapper
{
    private static final long serialVersionUID = 1L;

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
