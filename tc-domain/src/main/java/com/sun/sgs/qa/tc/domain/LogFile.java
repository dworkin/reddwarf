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

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Lob;
import javax.persistence.Basic;
import javax.persistence.FetchType;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "LogFile")
public class LogFile implements Serializable
{
    private Long id;
    private String log;
    
    public LogFile(String log)
    {
        this.setLog(log);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "log", nullable = false)
    public String getLog() { return log; }
    public void setLog(String log) { this.log = log; }

}
