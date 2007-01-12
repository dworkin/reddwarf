package com.sun.sgs.example.battleboard.client.swing;

import java.awt.Rectangle;

public interface Zapper {

    void zap(int x, int y, boolean fireball);

    void setSource(Rectangle r);
}
