/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.example.chat.client;

import java.math.BigInteger;

public class ChatUser {

    private final String name;
    private final BigInteger id;

    public ChatUser(String name, BigInteger id) {
        this.name = name;
        this.id = id;
    }

    public String getIdString() {
        return id.toString(16);
    }

    @Override
    public String toString() {
        return name + " [" + getIdString() + "]";
    }
}
