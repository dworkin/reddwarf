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

package com.projectdarkstar.tools.dtc.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Version;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "Property")
public class Property implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String description;
    private String property;
    private String value;
    

    public Property(String description,
                    String property,
                    String value)
    {
        this.setDescription(description);
        this.setProperty(property);
        this.setValue(value);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Version
    @Column(name = "versionNumber")
    public Long getVersionNumber() { return versionNumber; }
    protected void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    @Column(name = "description", nullable = false, length = 1024)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    @Column(name = "property", nullable = false)
    public String getProperty() { return property; }
    public void setProperty(String property) { this.property = property; }
    
    @Column(name = "value")
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
