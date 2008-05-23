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

package com.sun.sgs.qa.tc.domain;

import java.util.SortedSet;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "ServerApp")
public class ServerApp implements Serializable
{
    private Long id;
    private String name;
    private String description;
    private String className;
    private String classPath;
    
    private SortedSet<ServerAppConfig> configs;
    private PkgLibrary requiredPkg;
    
    public ServerApp(String name,
                     String description,
                     String className,
                     String classPath)
    {
        this.setName(name);
        this.setDescription(description);
        this.setClassName(className);
        this.setClassPath(classPath);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name= "description", nullable = false, length = 1024)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    @Column(name = "className", nullable = false)
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    
    @Column(name = "classPath", nullable = false)
    public String getClassPath() { return classPath; }
    public void setClassPath(String classPath) { this.classPath = classPath; }
    
    @OneToMany(mappedBy = "serverApp")
    public SortedSet<ServerAppConfig> getConfigs() { return configs; }
    public void setConfigs(SortedSet<ServerAppConfig> configs) { this.configs = configs; }
    
    @ManyToOne
    @JoinColumn(name = "requiredPkg")
    public PkgLibrary getRequiredPkg() { return requiredPkg; }
    public void setRequiredPkg(PkgLibrary requiredPkg) { this.requiredPkg = requiredPkg; }
    
}
