
/*
 * BoardListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 9:43:45 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.awt.Image;

import java.util.Map;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface BoardListener
{

    /**
     *
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap);

    /**
     *
     */
    public void changeBoard(Board board);

    /**
     *
     */
    public void updateSpaces(BoardSpace [] spaces);

    /**
     *
     */
    public void hearMessage(String message);

}
