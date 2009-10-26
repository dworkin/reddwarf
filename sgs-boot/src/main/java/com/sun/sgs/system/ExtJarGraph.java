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

package com.sun.sgs.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** A collection of extension jars used to determine ordering. */
class ExtJarGraph {

    // the location for an extension's properties
    static final String EXT_PROPERTIES_FILE = "META-INF/ext.properties";

    // the property for defining any dependencies
    static final String DEPENDENCY_PROPERTY = "DEPENDS_ON";

    // a map of all extension jars
    private final Map<String, JarNode> extNodes =
        new HashMap<String, JarNode>();

    // a collection of jars that depend on other jars
    private final Set<JarNode> dependencyRoots = new HashSet<JarNode>();

    // whether or not any preferences or dependencies are defined by the
    // the set of extension jars
    private boolean hasPreferences = false;
    private boolean hasDependencies = false;

    /** Creates an instance of {@code ExtJarGraph}. */
    ExtJarGraph() { }

    /**
     * Adds a jar file to this collection. This method checks that the jar
     * includes a manifest with required details. It also looks for optional
     * properties and verifies that they are correctly formatted.
     *
     * @param jar the extension jar to add
     */
    void addJarFile(JarFile jar) {
        JarNode node = null;

        // extract the name and version from the manifest, making sure that
        // the name is unique
        Manifest manifest = null;
        try {
            manifest = jar.getManifest();
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to get manifest from " +
                                            "jar file: " + jar.getName());
        }
        if (manifest == null) {
            throw new IllegalStateException("Manifest missing in extension " +
                                            "jar file: " + jar.getName());
        }
        Attributes attrs = manifest.getMainAttributes();
        String extName = attrs.getValue(Name.SPECIFICATION_TITLE);
        if (extName == null) {
            throw new IllegalStateException("Specification name required");
        }
        if (extNodes.containsKey(extName)) {
            throw new IllegalStateException("Found two extensions with the " +
                                            "same name: " + extName);
        }
        String extVersion = attrs.getValue(Name.SPECIFICATION_VERSION);
        if (extVersion == null) {
            throw new IllegalStateException("Specification version required");
        }

        // see if the jar file contains any darkstar properties
        JarEntry propertiesEntry = jar.getJarEntry(EXT_PROPERTIES_FILE);
        if (propertiesEntry != null) {
            Properties p = new Properties();
            try {
                p.load(jar.getInputStream(propertiesEntry));
            } catch (IOException ioe) {
                throw new IllegalStateException("Malformed properties in " +
                                                "ext jar: " + jar.getName());
            }
            node = new JarNode(extName, extVersion, p);
            hasPreferences = true;

            String dependencies = (String) p.remove(DEPENDENCY_PROPERTY);
            if (dependencies != null) {
                hasDependencies = true;
                for (String dependency : dependencies.split(":")) {
                    node.namedDependencies.add(dependency);
                }
            }
        } else {
            node = new JarNode(extName, extVersion);
        }

        extNodes.put(extName, node);
        dependencyRoots.add(node);
    }

    /**
     * Returns the name of a file containing all of the properties defined
     * in the collection of jar files, or null if no properties are defined.
     * This method will make sure that all dependencies are met, that there
     * are no circular dependenices, and that the properties are defined
     * correctly.
     *
     * @return a property filename or null
     * @throws IOException if there is a problem creating the property file
     */
    String getPropertiesFile() throws IOException {
        if (extNodes.isEmpty() || (!hasPreferences)) {
            return null;
        }
        if (hasDependencies) {
            checkDependencies();
        }

        // collect list properties in the right order
        Properties p = new Properties();
        StringBuilder servicesLine = new StringBuilder();
        StringBuilder managersLine = new StringBuilder();
        StringBuilder nodeTypesLine = new StringBuilder();
        StringBuilder authenticatorsLine = new StringBuilder();
        for (JarNode node : dependencyRoots) {
            buildProperties(node, p, servicesLine, managersLine, nodeTypesLine,
                            authenticatorsLine);
        }
        if (servicesLine.length() != 0) {
            p.setProperty("com.sun.sgs.ext.services", servicesLine.toString());
        }
        if (managersLine.length() != 0) {
            p.setProperty("com.sun.sgs.ext.managers", managersLine.toString());
        }
        if (nodeTypesLine.length() != 0) {
            p.setProperty("com.sun.sgs.ext.services.node.types",
                          nodeTypesLine.toString());
        }
        if (authenticatorsLine.length() != 0) {
            p.setProperty("com.sun.sgs.ext.authenticators",
                          authenticatorsLine.toString());
        }

        // generate the properties file
        File propFile = File.createTempFile("extProperties", null);
        FileOutputStream out = new FileOutputStream(propFile);
        try {
            p.store(out, "Temporary extension properties file");
        } finally {
            out.close();
        }
        return propFile.getAbsolutePath();
    }

    /** Check that all dependencies are met, and that there are no loops. */
    private void checkDependencies() {
        // scan all the jar nodes checking that all depdencies are available
        for (JarNode node : extNodes.values()) {
            for (String dependency :  node.namedDependencies) {
                JarNode dNode = extNodes.get(dependency);
                if (dNode == null) {
                    throw new IllegalStateException("Missing dependency: " +
                                                    dependency);
                }
                // if someone depends on dNode then it is removed from the
                // the root collection
                dependencyRoots.remove(dNode);
                node.dNodes.add(dNode);
            }
        }

        // see if there are any dependencies at all
        if (extNodes.size() == dependencyRoots.size()) {
            return;
        }

        // make sure there are no loops
        if (dependencyRoots.isEmpty()) {
            throw new IllegalStateException("Circular dependency not allowed");
        }
        Set<String> names = new HashSet<String>();
        for (JarNode node : dependencyRoots) {
            names.clear();
            loopCheck(node, names);
        }
    }

    /** Recursively check that a given node doesn't lead to a loop. */
    private static void loopCheck(JarNode node, Set<String> names) {
        if (names.contains(node.name)) {
            throw new IllegalStateException("Loop in dependent extensions: " +
                                            node.name);
        }
        names.add(node.name);
        for (JarNode dNode : node.dNodes) {
            loopCheck(dNode, new HashSet<String>(names));
        }
    }

    /** Collects all properties and multi-element lines. */
    private void buildProperties(JarNode node, Properties p,
                                 StringBuilder servicesLine,
                                 StringBuilder managersLine,
                                 StringBuilder nodeTypesLine,
                                 StringBuilder authenticatorsLine)
    {
        // gather properties from depdencies first
        for (JarNode dNode : node.dNodes) {
            buildProperties(dNode, p, servicesLine, managersLine, nodeTypesLine,
                            authenticatorsLine);
        }

        // include this node's properties if they haven't already been included
        if (extNodes.remove(node.name) != null) {
            // remove the standard list properties to combine in a
            // separate collection
            Properties nodeProps = node.properties;
            String managers = (String) nodeProps.remove("com.sun.sgs.managers");
            int managerCount = getElementCount(managers);
            String services = (String) nodeProps.remove("com.sun.sgs.services");
            int serviceCount = getElementCount(services);
            String nodeTypes = (String) nodeProps.remove(
                    "com.sun.sgs.services.node.types");
            int nodeTypeCount = getElementCount(nodeTypes);

            // verify that the manager and service counts line up, or if
            // there are no managers then there is at most only one service
            if (managerCount != 0) {
                if (managerCount != serviceCount) {
                    throw new IllegalStateException("Mis-matched Manager " +
                                                    "and Service count for " +
                                                    node.name);
                }
            } else {
                if (serviceCount > 1) {
                    throw new IllegalStateException("Missing Managers for " +
                                                    node.name);
                }
            }

            // verify that there are either no node types, or exactly the same
            // number as there are services
            if (nodeTypeCount != 0 && nodeTypeCount != serviceCount) {
                throw new IllegalStateException("Mis-matched Node Type " +
                                                "and Service count for " +
                                                node.name);
            }

            // if there are services then add them after figuring out how to
            // modify the manager and node types lines correctly
            if (serviceCount != 0) {
                if (managerCount == 0) {
                    if (servicesLine.length() != 0) {
                        // there are no new managers but there are previous
                        // services so we need to pad a ":" to the line
                        managersLine.append(":");
                    }
                } else {
                    if ((servicesLine.length() != 0) &&
                        (managersLine.length() == 0))
                    {
                        // there were previously services but no managers, so
                        // pre-pend a ":" to the line
                        addToLine(managersLine, ":" + managers);
                    } else {
                        // no padding is needed, just add the managers
                        addToLine(managersLine, managers);
                    }
                }

                if (nodeTypeCount == 0) {
                    for (int i = 0; i < serviceCount; i++) {
                        addToLine(nodeTypesLine, "ALL");
                    }
                } else {
                    addToLine(nodeTypesLine, nodeTypes);
                }

                addToLine(servicesLine, services);
            }

            String authenticators =
                (String) nodeProps.remove("com.sun.sgs.app.authenticators");
            if ((authenticators != null) && (authenticators.length() != 0)) {
                addToLine(authenticatorsLine, authenticators);
            }
            // merge any remaining properties, failing if the same property
            // is assigned different values by separate extensions
            for (String key : nodeProps.stringPropertyNames()) {
                String value = (String) nodeProps.getProperty(key);
                Object oldValue = p.setProperty(key, value);
                if ((oldValue != null) && (!value.equals(oldValue))) {
                    throw new IllegalStateException("Multiple values for " +
                                                    "property: " + key);
                }
            }
        }
    }

    /** Count the number of colon-separated elements in the string. */
    private int getElementCount(String str) {
        if ((str == null) || (str.length() == 0)) {
            return 0;
        }
        int count = 0;
        int pos = -1;
        do {
            count++;
            pos = str.indexOf(':', pos + 1);
        } while (pos != -1);
        return count;
    }
    

    /** Adds an element to a multi-element line. */
    private static void addToLine(StringBuilder buf, String str) {
        if (buf.length() != 0) {
            buf.append(":" + str);
        } else {
            buf.append(str);
        }
    }

    /** Private class used to maintain state for a single extension jar. */
    private static class JarNode {
        final String name;
        final String version;
        final Properties properties;
        final Set<String> namedDependencies = new HashSet<String>();
        final Set<JarNode> dNodes = new HashSet<JarNode>();
        JarNode(String name, String version) {
            this.name = name;
            this.version = version;
            this.properties = null;
        }
        JarNode(String name, String version, Properties properties) {
            this.name = name;
            this.version = version;
            this.properties = properties;
        }
        public boolean equals(Object o) {
            if (!(o instanceof JarNode)) {
                return false;
            }
            return name.equals(((JarNode) o).name);
        }
        public int hashCode() {
            return name.hashCode();
        }
    }

}
