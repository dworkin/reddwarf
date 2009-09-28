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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.impl.service.nodemap.affinity.single;

import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.profile.ProfileCollector;

/**
 * Affinity group finder stats for single node label propagation algorithm.
 */
public class SingleNodeFinderStats extends AffinityGroupFinderStats {
    /**
     * Constructs the MXBean for affinity group finder information.
     * @param collector the profile collector
     * @param stopIter the maximum iterations a run will perform
     */
    public SingleNodeFinderStats(ProfileCollector collector, int stopIter) {
        super(collector, stopIter);
    }

    // Package private updators
    void iterationsSample(long sample) {
        iterations.addSample(sample);
    }

    void runtimeSample(long sample) {
        runtime.addSample(sample);
    }

    void runsCountInc() {
        runs.incrementCount();
    }

    void failedCountInc() {
        failed.incrementCount();
    }

    void stoppedCountInc() {
        stopped.incrementCount();
    }

    void setNumGroups(int value) {
        numGroups = value;
    }
}
