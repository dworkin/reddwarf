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
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Version;
import javax.persistence.Lob;
import javax.persistence.Basic;
import javax.persistence.FetchType;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "PkgLibrary")
public class PkgLibrary implements Serializable
{
    private Long id;
    private Long versionNumber;
    
    private String name;
    private byte[] file;
    
    private List<PkgLibraryTag> tags;
    
    public PkgLibrary(String name,
                      byte[] file)
    {
        this.setName(name);
        this.setFile(file);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Version
    @Column(name = "versionNumber")
    public Long getVersionNumber() { return versionNumber; }
    protected void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    @Column(name = "name", nullable = false, unique = true)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file", nullable = false)
    public byte[] getFile() { return file; }
    public void setFile(byte[] file) { this.file = file; }
    
    @ManyToMany
    @OrderBy("tag")
    @JoinTable(name = "pkgLibraryTags",
               joinColumns = @JoinColumn(name = "pkgLibraryId"),
               inverseJoinColumns = @JoinColumn(name = "pkgLibraryTagId"))
    public List<PkgLibraryTag> getTags() { return tags; }
    public void setTags(List<PkgLibraryTag> tags) { this.tags = tags; }
}
