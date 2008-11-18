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
import java.util.ArrayList;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a tag entity used to categorize {@link ClientApp}
 * objects.
 */
@Entity
@Table(name = "ClientAppTag")
public class ClientAppTag implements Serializable
{
    private Long id;
    private String tag;
    
    private List<ClientApp> apps;
    
    public ClientAppTag() {}
    
    public ClientAppTag(String tag)
    {
        this.setTag(tag);
        this.setApps(new ArrayList<ClientApp>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name = "tag", nullable = false, unique = true)
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    
    /**
     * Returns the list of {@link ClientApp} objects that are tagged
     * with this tag.
     * 
     * @return client apps tagged with this tag.
     */
    @ManyToMany(mappedBy = "tags")
    @OrderBy("name")
    public List<ClientApp> getApps() { return apps; }
    public void setApps(List<ClientApp> apps) { this.apps = apps; }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof ClientAppTag) || o == null) return false;

        ClientAppTag other = (ClientAppTag)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getTag(), other.getTag()) &&
                ObjectUtils.equals(this.getApps(), other.getApps());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashTag = 31*hash + ObjectUtils.hashCode(this.getTag());
        return hashId + hashTag;
    }
}

