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

import java.util.List;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "TestExecutionTag")
public class PkgLibraryTag implements Serializable
{
    private Long id;
    private String tag;
    
    private List<PkgLibrary> libraries;
    
    public PkgLibraryTag(String tag)
    {
        this.setTag(tag);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name = "tag", nullable = false, unique = true)
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    
    @ManyToMany(mappedBy = "tags")
    @OrderBy("name")
    public List<PkgLibrary> getLibraries() { return libraries; }
    public void setLibraries(List<PkgLibrary> libraries) { this.libraries = libraries; }
}

