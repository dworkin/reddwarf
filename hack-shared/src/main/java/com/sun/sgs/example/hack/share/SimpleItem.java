/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import com.sun.sgs.example.hack.share.ItemInfo.Item;
import com.sun.sgs.example.hack.share.ItemInfo.ItemType;

import java.io.Serializable;


/**
 *
 */
public class SimpleItem implements Item, Serializable {

    private final String name;

    private final long id;

    private final ItemType type;

    public SimpleItem(ItemType type, long id, String name) {
	this.type = type;
	this.id = id;
	this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public long getItemId() {
	return id;
    }
    
    /**
     * {@inheritDoc}
     */
    public ItemType getItemType() {
	return type;
    }
    
    /**
     * {@inheritDoc}
     */
    public String getName() {
	return name;
    }
}
