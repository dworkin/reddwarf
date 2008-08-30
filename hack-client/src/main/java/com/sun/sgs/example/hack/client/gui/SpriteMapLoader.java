/*
 * This work is hereby released into the Public Domain.  To view a
 * copy of the public domain dedication, visit
 * http://creativecommons.org/licenses/publicdomain/ or send a letter
 * to Creative Commons, 171 Second Street, Suite 300, San Francisco,
 * California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.gui;

/**
 * A package-private class for loading the {@link SpriteMap} class
 * defined in {@link SpriteMap#SPRITE_MAP_CLASS_PROPERTY}.
 */
final class SpriteMapLoader {

    /**
     * Loads the {@code SpriteMap} class defined in the system
     * properties.
     *
     * @return an instance of the sprite map defined in the system
     *         property
     *
     * @throws IllegalStateException if the defined class is null, or
     *         if the class cannot be instantiated for any reason
     */
    static SpriteMap loadSystemSpriteMap() {
	String className = System.getProperties().
	    getProperty(SpriteMap.SPRITE_MAP_CLASS_PROPERTY);
	if (className == null) {
	    throw new IllegalStateException("SpriteMap class has not " + 
					    "been defined");
	}
	try {
	    Object o = Class.forName(className).newInstance();
	    if (!(o instanceof SpriteMap)) {
		throw new IllegalStateException("Provided class is not an " + 
						"instance of SpriteMap");
	    }
	    return (SpriteMap)o;
	}
	catch (ClassNotFoundException cnfe) {
	    throw new IllegalStateException("SpriteMap class could not found", 
					    cnfe);
	}
	catch (InstantiationException ie) {
	    throw new IllegalStateException("SpriteMap class could not be " +
					    "instantiated", ie);
	}
	catch (IllegalAccessException iae) {
	    throw new IllegalStateException("SpriteMap class could not be " +
					    "instantiated", iae);
	}
    }

}