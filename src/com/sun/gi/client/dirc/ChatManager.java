/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 *
 * ChatManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 6:11:10 PM
 * Desc: 
 *
 */

package com.sun.gi.client.dirc;

import java.nio.ByteBuffer;
import java.util.HashSet;

/**
 * @since 1.0
 * @author Seth Proctor
 * @author James Megquier
 */
public interface ChatManager {

    /**
     * Process the user input from a Chat pane.
     *
     * @param text the user input to process
     */
    public void handleChatInput(String text);
}
