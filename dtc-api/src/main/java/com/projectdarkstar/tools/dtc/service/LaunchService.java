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

package com.projectdarkstar.tools.dtc.service;

import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import com.projectdarkstar.tools.dtc.data.PkgLibraryDTO;

/**
 * This interface is the central API used by clients to load test
 * suites into the execution queue to be picked and executed by
 * the test cluster execution daemon.
 * 
 * It is intended to be a remote interface exposing a stateless session
 * EJB3.0 bean.
 */
public interface LaunchService
{
    /**
     * <p>
     * Schedule execution of the TestExecution with the given id.
     * A TestQueue object will be created as a wrapper around the given
     * TestExecution.  The execution daemon should then pick up the
     * test from the queue and run the complete TestExecution as it
     * is specified.
     * </p>
     * 
     * @param testExecutionId id of the TestExecution to execute
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void runTestExecution(Long testExecutionId)
            throws DTCServiceException;
    
    
    /**
     * <p>
     * Schedule execution of the TestSuite with the given id.
     * This method specifically will create a TestExecution object
     * derived from the TestSuite object with the given id and will
     * load it into the execution queue as a TestQueue object.
     * </p>
     * 
     * <p>
     * The newly created TestExecution will be assigned the given name
     * as its name attribute.  It will also be associated with a set of
     * tags represented by the comma/space separated list of given tags.
     * For each tag in the list, either a new TestExecutionTag entity
     * will be created, or the corresponding entity from persistent
     * storage will be used.
     * </p>
     * 
     * @param testSuiteId id of the TestSuite to execute
     * @param name name to assign to the new TestExecution
     * @param tags comma/space separated list of tags to assign to the new TestExecution
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void runTestSuite(Long testSuiteId,
                             String name,
                             String tags)
            throws DTCServiceException;
    
    /**
     * <p>
     * Schedule execution of the TestSuite with the given id
     * to be run against the given darkstar package.  This method will
     * create a TestExecution object derived from the TestSuite object
     * with the given id.  However, the PkgLibrary required for darkstar with 
     * the given TestSuite will be replaced with the darkstarPkg.  A
     * TestQueue will then be created to schedule this suite for execution.
     * </p>
     * 
     * <p>
     * The newly created TestExecution will be assigned the given name
     * as its name attribute.  It will also be associated with a set of
     * tags represented by the comma/space separated list of given tags.
     * For each tag in the list, either a new TestExecutionTag entity
     * will be created, or the corresponding entity from persistent
     * storage will be used.
     * </p>
     * 
     * @param testSuiteId id of the TestSuite to execute
     * @param name name to assign to the new TestExecution
     * @param tags comma/space separated list of tags to assign to the new TestExecution
     * @param darkstarPkg package library of the darkstar package to test
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void runTestSuiteAgainstNewDarkstar(Long testSuiteId,
                                               String name,
                                               String tags,
                                               PkgLibraryDTO darkstarPkg)
            throws DTCServiceException;
    
    /**
     * <p>
     * Schedule execution of the TestSuite with the given id to be
     * run against the given server application package.  This method will
     * create a TestExecution object derived from the TestSuite object
     * with the given id.  However, the PkgLibrary required for the
     * serverApp in each of the TestSuite's TestSpec will be replaced by the 
     * serverPkg.  A TestQueue will then be created to schedule this 
     * suite for execution.
     * </p>
     * 
     * <p>
     * The newly created TestExecution will be assigned the given name
     * as its name attribute.  It will also be associated with a set of
     * tags represented by the comma/space separated list of given tags.
     * For each tag in the list, either a new TestExecutionTag entity
     * will be created, or the corresponding entity from persistent
     * storage will be used.
     * </p>
     * 
     * <p>
     * Note that the assumption made with this method is that the TestSpec
     * objects of the given TestSuite are <em>all</em> associated with the
     * same server application.  If they are not, the results may be 
     * unexpected.
     * </p>
     * 
     * @param testSuiteId id of the TestSuite to execute
     * @param name name to assign to the new TestExecution
     * @param tags comma/space separated list of tags to assign to the new TestExecution
     * @param serverPkg package library of the server application to test
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void runTestSuiteAgainstNewServerApp(Long testSuiteId,
                                                String name,
                                                String tags,
                                                PkgLibraryDTO serverPkg)
            throws DTCServiceException;

    

    /**
     * <p>
     * Schedule execution of a new TestExecution based on the TestExecution
     * with the given id. This method will create a new TestExecution
     * which is identical to the TestExecution with the given id and schedule
     * it to be run by creating a TestQueue object.
     * </p>
     * 
     * <p>
     * The newly created TestExecution will be assigned the given name
     * as its name attribute.  It will also be associated with a set of
     * tags represented by the comma/space separated list of given tags.
     * For each tag in the list, either a new TestExecutionTag entity
     * will be created, or the corresponding entity from persistent
     * storage will be used.
     * </p>
     * 
     * <p>
     * Note that there is a distinction between using this method and 
     * simply using the {@link #runTestSuite} method against
     * the TestSuite id from the given TestExecution.  The difference is that
     * this method guarantees that the execution configuration will be
     * completely duplicated.  This is not the case when running against
     * a TestSuite because the TestSuite may have changed.
     * </p>
     * 
     * @param testExecutionId id of the TestExecution to rerun
     * @param name name to assign to the new TestExecution
     * @param tags comma/space separated list of tags to assign to the new TestExecution
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void rerunTestExecution(Long testExecutionId,
                                   String name,
                                   String tags)
            throws DTCServiceException;
    
    /**
     * <p>
     * Schedule execution of a new TestExecution based on the TestExecution
     * with the given id to be run against the given darkstar application.
     * This method will create a new TestExecution which is identical
     * to the TestExecution with the given id.  It will then modify
     * the darkstar package of the new TestExecution to use the new darkstar
     * package and schedule the test to be run by creating a TestQueue object.
     * </p>
     * 
     * <p>
     * The newly created TestExecution will be assigned the given name
     * as its name attribute.  It will also be associated with a set of
     * tags represented by the comma/space separated list of given tags.
     * For each tag in the list, either a new TestExecutionTag entity
     * will be created, or the corresponding entity from persistent
     * storage will be used.
     * </p>
     * 
     * @param testExecutionId id of the TestExecution to rerun
     * @param name name to assign to the new TestExecution
     * @param tags comma/space separated list of tags to assign to the new TestExecution
     * @param darkstarPkg package library of the darkstar package to test
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void rerunTestExecutionAgainstNewDarkstar(Long testExecutionId,
                                                     String name,
                                                     String tags,
                                                     PkgLibraryDTO darkstarPkg)
            throws DTCServiceException;
    
    /**
     * <p>
     * Schedule execution of a new TestExecution based on the TestExecution
     * with the given id to be run against the given server application.
     * This method will create a new TestExecution which is identical to
     * the TestExecution with the given id.  It will then modify the server
     * application package specified for each of the tests to use to the
     * given server package library and schedule the tests to be run
     * by creating a TestQueue object.
     * </p>
     * 
     * <p>
     * The newly created TestExecution will be assigned the given name
     * as its name attribute.  It will also be associated with a set of
     * tags represented by the comma/space separated list of given tags.
     * For each tag in the list, either a new TestExecutionTag entity
     * will be created, or the corresponding entity from persistent
     * storage will be used.
     * </p>
     * 
     * @param testExecutionId id of the TestExecution to rerun
     * @param name name to assign to the new TestExecution
     * @param tags comma/space separated list of tags to assign to the new TestExecution
     * @param darkstarPkg library of the server application package to test
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void rerunTestExecutionAgainstNewServerApp(Long testExecutionId,
                                                      String name,
                                                      String tags,
                                                      PkgLibraryDTO darkstarPkg)
            throws DTCServiceException;
}
