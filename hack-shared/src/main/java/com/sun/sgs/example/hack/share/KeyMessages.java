/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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
