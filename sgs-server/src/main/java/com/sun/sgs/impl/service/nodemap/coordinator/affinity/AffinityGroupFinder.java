/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.nodemap.coordinator.affinity;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TransactionProxy;

/**
 * An affinity groups finder will find sets of identities that have formed a
 * community.<p>
 *
 * The class that implements this
 * interface should be public, not abstract, and should provide a public
 * constructor with {@link Properties}, {@link AffinityGroupCoordinator},
 * {@link ComponentRegistry}, and {@link TransactionProxy} parameters.<p>
 *
 * A newly constructed coordinator should be in the disabled state.
 */
public interface AffinityGroupFinder {

    /**
     * Enable finding groups. If the finder is enabled, calling this method
     * will have no effect.
     */
    void enable();

    /**
     * Disable finding groups. If the finder is disabled, calling this method
     * will have no effect.
     */
    void disable();

    /**
     * Shutdown the finder. The finder is disabled and
     * all resources released. Any further method calls made on the finder
     * will result in a {@code IllegalStateException} being thrown.
     */
    void shutdown();
}
