/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.chat.client;

import java.math.BigInteger;

/**
 * TODO doc
 */
public class ChatMember {

    private final String name;
    private final BigInteger id;

    public ChatMember(String name, BigInteger id) {
        this.name = name;
        this.id = id;
    }

    /**
     * Returns the user name associated with the this {@code ChatMember},
     * if any.
     * 
     * @return the name associated with this session, or {@code null}
     */
    public String getName() {
        return name;
    }
    
    public BigInteger getId() {
        return id;
    }

    @Override
    public String toString() {
        return getName() + " [" + getId().toString(16) + "]";
    }
}
