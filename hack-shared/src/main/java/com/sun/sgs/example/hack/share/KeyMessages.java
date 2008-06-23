/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.hack.share;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a simple collection of messages to define key presses.
 */
public class KeyMessages {

    private static final Map<Integer,KeyMessages.Type> ordinalToEnum =
	new HashMap<Integer,KeyMessages.Type>();
    
    static {       
	for (KeyMessages.Type mesg : KeyMessages.Type.values()) {
	    ordinalToEnum.put(mesg.ordinal(), mesg);
	}
    }

    public static int encode(KeyMessages.Type mesg) {
	return mesg.ordinal();
    }

    public static KeyMessages.Type decode(int encodedType) {
	return ordinalToEnum.get(encodedType);
    }
    

    public enum Type {
	NONE,
	UP,
	DOWN,
	LEFT,
	RIGHT,
	TAKE
    }

}
