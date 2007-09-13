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


package com.sun.sgs.impl.kernel.profile;

/**
 * A collection of common properties used by {@link
 * com.sun.sgs.kernel.ProfileOperationListener} implementations.
 */
public interface ProfileProperties {

    /**
     * The property for defining the number task to summarize for
     * windowed {@code com.sun.sgs.kernel.ProfileOperationListener}
     * implementations.  The value assigned to the property must be an
     * integer.
     */
    public static final String WINDOW_SIZE =
	"com.sun.sgs.kernel.profile.listener.window.size";


}