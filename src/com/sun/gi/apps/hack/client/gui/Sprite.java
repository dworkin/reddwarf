
/*
 * Sprite.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Feb 17, 2006	 2:28:05 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import java.awt.Image;


/**
 *
 */
class Sprite
{

    //
    private int id;

    //
    private String name;

    //
    private Image image;

    /**
     *
     */
    public Sprite(int id, String name, Image image) {
        this.id = id;
        this.name = name;
        this.image = image;
    }

    /**
     *
     */
    public int getId() {
        return id;
    }

    /**
     *
     */
    public String getName() {
        return name;
    }

    /**
     *
     */
    public Image getImage() {
        return image;
    }

}
