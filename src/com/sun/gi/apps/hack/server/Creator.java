
/*
 * Creator.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Wed Mar  1, 2006	 3:09:23 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;


/**
 * FIXME: This is still a work in progress
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class Creator implements Game
{

    /**
     *
     */
    public Creator() {
        
    }

    /**
     *
     */
    public void join(Player player) {

    }

    /**
     *
     */
    public void leave(Player player) {

    }

    /**
     *
     */
    public MessageHandler createMessageHandler() {
        return new CreatorMessageHandler();
    }

    /**
     *
     */
    public String getName() {
        return "";
    }

    /**
     *
     */
    public int numPlayers() {
        return 0;
    }

}
