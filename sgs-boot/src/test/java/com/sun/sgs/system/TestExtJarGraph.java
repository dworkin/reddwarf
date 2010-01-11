/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 * --
 */

package com.sun.sgs.system;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.jar.JarFile;
import org.junit.Assert;
import org.junit.Test;

/** Test the ExtJarGraph class used to handle extension jar files. */
public class TestExtJarGraph {

    // the relative path to the directory containing test jar files
    private static final String JAR_DIRECTORY =
        "src" + File.separator + "test" + File.separator +
        "test-jars" + File.separator;

    // the set of jar files used for the tests
    private static final File JAR_A =
        new File(JAR_DIRECTORY + "A.jar");
    private static final File JAR_B =
        new File(JAR_DIRECTORY + "B.jar");
    private static final File JAR_C =
        new File(JAR_DIRECTORY + "C.jar");
    private static final File JAR_NO_NAME =
        new File(JAR_DIRECTORY + "NoName.jar");
    private static final File JAR_NO_VERSION =
        new File(JAR_DIRECTORY + "NoVersion.jar");
    private static final File JAR_NO_MANIFEST =
        new File(JAR_DIRECTORY + "NoManifest.jar");
    private static final File JAR_NO_PROPERTIES =
        new File(JAR_DIRECTORY + "NoProperties.jar");
    private static final File JAR_MIS_MATCHED_SERVICES =
        new File(JAR_DIRECTORY + "MisMatchedServices.jar");
    private static final File JAR_MIS_MATCHED_NODE_TYPES =
        new File(JAR_DIRECTORY + "MisMatchedNodeTypes.jar");
    private static final File JAR_A_WITH_SERVICES =
        new File(JAR_DIRECTORY + "AWithServices.jar");
    private static final File JAR_B_WITH_SERVICES =
        new File(JAR_DIRECTORY + "BWithServices.jar");
    private static final File JAR_C_WITH_SERVICES =
        new File(JAR_DIRECTORY + "CWithServices.jar");
    private static final File JAR_D_WITH_SERVICES =
        new File(JAR_DIRECTORY + "DWithServices.jar");
    private static final File JAR_A_PROPERTY_COLLISION =
        new File(JAR_DIRECTORY + "APropertyCollision.jar");
    private static final File JAR_A_PROPERTY_DUPLICATE =
        new File(JAR_DIRECTORY + "APropertyDuplicate.jar");
    private static final File JAR_B_DEPENDS_ON_A =
        new File(JAR_DIRECTORY + "BDependsOnA.jar");
    private static final File JAR_C_DEPENDS_ON_A =
        new File(JAR_DIRECTORY + "CDependsOnA.jar");
    private static final File JAR_A_DEPENDS_ON_B =
        new File(JAR_DIRECTORY + "ADependsOnB.jar");
    private static final File JAR_D_DEPENDS_ON_A_AND_B =
        new File(JAR_DIRECTORY + "DDependsOnAAndB.jar");
    private static final File JAR_B_DEPENDS_ON_A_AND_C =
        new File(JAR_DIRECTORY + "BDependsOnAAndC.jar");

    @Test public void testEmptyGraph() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.getPropertiesFile();
    }

    @Test(expected = IllegalStateException.class)
    public void testJarMissingManifest() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_NO_MANIFEST));
    }

    @Test(expected = IllegalStateException.class)
    public void testJarMissingName() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_NO_NAME));
    }

    @Test(expected = IllegalStateException.class)
    public void testJarMissingVersion() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_NO_VERSION));
    }

    @Test public void testSingleJar() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A));
        String fileName = graph.getPropertiesFile();
        Properties p = new Properties();
        p.load(new FileInputStream(fileName));
        Assert.assertTrue(p.getProperty("a.property").equals("A"));
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateNamedJar() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        try {
            graph.addJarFile(new JarFile(JAR_A));
        } catch (IllegalStateException ise) {
            throw new RuntimeException("Unexpected Failure", ise);
        }
        graph.addJarFile(new JarFile(JAR_A));
    }

    @Test public void testJarNoProperties() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_NO_PROPERTIES));
        graph.getPropertiesFile();
    }

    @Test(expected = IllegalStateException.class)
    public void testJarConflictingProperties() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        try {
            graph.addJarFile(new JarFile(JAR_A));
            graph.addJarFile(new JarFile(JAR_A_PROPERTY_COLLISION));
        } catch (IllegalStateException ise) {
            throw new RuntimeException("Unexpected Failure", ise);
        }
        graph.getPropertiesFile();
    }

    @Test public void testJarDuplicateProperties() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A));
        graph.addJarFile(new JarFile(JAR_A_PROPERTY_DUPLICATE));
        String fileName = graph.getPropertiesFile();
        Properties p = new Properties();
        p.load(new FileInputStream(fileName));
        Assert.assertTrue(p.getProperty("a.property").equals("A"));
    }

    @Test(expected = IllegalStateException.class)
    public void testJarMisMatchedServices() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_MIS_MATCHED_SERVICES));
        graph.getPropertiesFile();
    }

    @Test(expected = IllegalStateException.class)
    public void testJarMisMatchedNodeTypes() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_MIS_MATCHED_NODE_TYPES));
        graph.getPropertiesFile();
    }

    @Test public void testJarMergedServicesEmptyManager() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A_WITH_SERVICES));
        graph.addJarFile(new JarFile(JAR_B_WITH_SERVICES));
        String fileName = graph.getPropertiesFile();
        Properties p = new Properties();
        p.load(new FileInputStream(fileName));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.services").
                          equals("com.example.Service1:com.example.Service2:" +
                                 "com.example.Service3"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.managers").
                          equals(":com.example.Manager2:"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.services.node.types").
                          equals("ALL:CORE:APP"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.authenticators").
                          equals("com.example.Authenticator1"));
    }

    @Test public void testJarMergedServices() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_C_WITH_SERVICES));
        graph.addJarFile(new JarFile(JAR_B_WITH_SERVICES));
        String fileName = graph.getPropertiesFile();
        Properties p = new Properties();
        p.load(new FileInputStream(fileName));

        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.services").
                          equals("com.example.Service2:com.example.Service3:" +
                                 "com.example.Service4"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.managers").
                          equals("com.example.Manager2::com.example.Manager4"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.services.node.types").
                          equals("CORE:APP:CORE_OR_APP"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.authenticators").
                          equals("com.example.Authenticator1:" +
                                 "com.example.Authenticator2"));
    }

    @Test
    public void testJarMergedServicesNoNodeTypes() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_D_WITH_SERVICES));
        graph.addJarFile(new JarFile(JAR_A_WITH_SERVICES));
        String fileName = graph.getPropertiesFile();
        Properties p = new Properties();
        p.load(new FileInputStream(fileName));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.services").
                equals("com.example.Service5:com.example.Service6:" +
                       "com.example.Service7:com.example.Service1"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.managers").
                equals("com.example.Manager5:com.example.Manager6:" +
                       "com.example.Manager7:"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.services.node.types").
                equals("ALL:ALL:ALL:ALL"));
        Assert.assertTrue(p.getProperty("com.sun.sgs.ext.authenticators").
                equals("com.example.Authenticator3:" +
                       "com.example.Authenticator4"));
    }

    @Test public void testMultipleJarNoDependency() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A));
        graph.addJarFile(new JarFile(JAR_B));
        graph.addJarFile(new JarFile(JAR_C));
        String fileName = graph.getPropertiesFile();
        Properties p = new Properties();
        p.load(new FileInputStream(fileName));
        Assert.assertTrue(p.getProperty("a.property").equals("A"));
        Assert.assertTrue(p.getProperty("b.property").equals("B"));
    }

    @Test public void testValidDepdency() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A));
        graph.addJarFile(new JarFile(JAR_B_DEPENDS_ON_A));
        graph.getPropertiesFile();
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingDependency() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        try {
            graph.addJarFile(new JarFile(JAR_B_DEPENDS_ON_A));
        } catch (IllegalStateException ise) {
            throw new RuntimeException("Unexpected Failure", ise);
        }
        graph.getPropertiesFile();
    }

    @Test public void testValidMultipleDepdency() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A));
        graph.addJarFile(new JarFile(JAR_D_DEPENDS_ON_A_AND_B));
        graph.addJarFile(new JarFile(JAR_B));
        graph.getPropertiesFile();
    }

    @Test public void testValidDepdencyMultipleRoots() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A));
        graph.addJarFile(new JarFile(JAR_B_DEPENDS_ON_A));
        graph.addJarFile(new JarFile(JAR_C_DEPENDS_ON_A));
        graph.getPropertiesFile();
    }

    @Test(expected = IllegalStateException.class)
    public void testCircularDepdencyNoRoot() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        try {
            graph.addJarFile(new JarFile(JAR_B_DEPENDS_ON_A));
            graph.addJarFile(new JarFile(JAR_A_DEPENDS_ON_B));
        } catch (IllegalStateException ise) {
            throw new RuntimeException("Unexpected Failure", ise);
        }
        graph.getPropertiesFile();
    }

    @Test(expected = IllegalStateException.class)
    public void testRootedCircularDepdency() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        try {
            graph.addJarFile(new JarFile(JAR_B_DEPENDS_ON_A));
            graph.addJarFile(new JarFile(JAR_C_DEPENDS_ON_A));
            graph.addJarFile(new JarFile(JAR_A_DEPENDS_ON_B));
        } catch (IllegalStateException ise) {
            throw new RuntimeException("Unexpected Failure", ise);
        }
        graph.getPropertiesFile();
    }

    @Test public void testDeeperDependency() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        graph.addJarFile(new JarFile(JAR_A));
        graph.addJarFile(new JarFile(JAR_D_DEPENDS_ON_A_AND_B));
        graph.addJarFile(new JarFile(JAR_B_DEPENDS_ON_A_AND_C));
        graph.addJarFile(new JarFile(JAR_C));
        graph.getPropertiesFile();
    }

    @Test(expected = IllegalStateException.class)
    public void testDeeperCircularDependency() throws Exception {
        ExtJarGraph graph = new ExtJarGraph();
        try {
            graph.addJarFile(new JarFile(JAR_A_DEPENDS_ON_B));
            graph.addJarFile(new JarFile(JAR_D_DEPENDS_ON_A_AND_B));
            graph.addJarFile(new JarFile(JAR_B_DEPENDS_ON_A_AND_C));
            graph.addJarFile(new JarFile(JAR_C_DEPENDS_ON_A));
        } catch (IllegalStateException ise) {
            throw new RuntimeException("Unexpected Failure", ise);
        }
        graph.getPropertiesFile();
    }

}
