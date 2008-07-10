/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of the Darkstar Test Cluster
 *
 * Darkstar Test Cluster is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Darkstar Test Cluster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.projectdarkstar.tools.dtc.runners;

import com.projectdarkstar.tools.dtc.data.TestExecutionResultDTO;
import com.projectdarkstar.tools.dtc.service.RuntimeExecutionsService;
import com.projectdarkstar.tools.dtc.drones.MasterDrone;

/**
 * The TestRunner interface is the main interface that must be implemented
 * in order to run a suite of tests in the Darkstar Test Cluster.  It is
 * responsible for taking a test configuration, running it on the cluster,
 * and collecting and reporting the results back to persistent storage.
 */
public interface TestRunner 
{

    /**
     * Run the specified test according to its specification.
     * The configuration includes all of the information necessary to
     * run the test application.  It also includes the hardware resources
     * where each component should be run.  This method should supply the
     * logic that decides how to run the test, and also the decision
     * about whether or not the test passes or fails.  Test result logs
     * should be updated using the given service, and the test result
     * should be updated as well once the test completes.
     * 
     * @param test test to run
     * @param service service interface used to update test results
     * @param masterDrone master drone which must be passed to slaves in order to receive feedback
     */
    public void run(TestExecutionResultDTO test,
                    RuntimeExecutionsService service,
                    MasterDrone masterDrone);
    
}
