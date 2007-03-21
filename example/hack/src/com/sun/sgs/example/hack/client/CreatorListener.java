/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */


package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.CharacterStats;


/**
 * This interface defines a class that listens for creator messages.
 */
public interface CreatorListener
{

    /**
     * Notifies the listener of new character statistics.
     *
     * @param id the character's identifier
     * @param stats the new statistics
     */
    public void changeStatistics(int id, CharacterStats stats);

}
