package com.sun.sgs.example.battleboard.client.swing;

import java.awt.*;
import javax.swing.*;

public class Messages extends JPanel {

    private static final long serialVersionUID = 1L;

    private JLabel label;
    private Color BGCOLOR = new Color(150, 130, 130);
    
    public Messages() {
        label = new JLabel("  ");
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 30, 10));
        setLayout(new BorderLayout());
        add(label);
    }
    
    public void paintComponent(Graphics g1) {
        Graphics2D g = (Graphics2D)g1;
        g.setColor(Color.black);
        int wid = getWidth();
        int hgt = getHeight();
        g.fillRect(0, 0, wid, hgt);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
		RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BGCOLOR);
        g.fillRoundRect(3, 3, wid - 6, hgt - 20, 16, 16);
    }
    
    public void addMessage(String message) {
        label.setText(message);
        repaint();
    }
}
