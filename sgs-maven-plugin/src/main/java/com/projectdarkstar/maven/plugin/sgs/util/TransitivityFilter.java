/*
 * Copyright (c) 2009, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.projectdarkstar.maven.plugin.sgs.util;

import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.artifact.Artifact;
import org.codehaus.plexus.util.StringUtils;

import java.util.Set;
import java.util.Iterator;

/**
 * Provided as a workaround due to a bug in {@code ProjectTransitivityFilter}
 */
public class TransitivityFilter extends ProjectTransitivityFilter {
    
    private Set directDependencies;
    
    public TransitivityFilter(Set directDependencies, boolean excludeTransitive) {
        super(directDependencies, excludeTransitive);
        this.directDependencies = directDependencies;
    }
    
    public boolean artifactIsADirectDependency(Artifact artifact) {
        boolean result = false;
        Iterator iterator = this.directDependencies.iterator();
        while (iterator.hasNext()) {
            Artifact dependency = (Artifact) iterator.next();

            if (StringUtils.equals(dependency.getGroupId(), artifact.getGroupId()) &&
                    StringUtils.equals(dependency.getArtifactId(), artifact.getArtifactId()) &&
                    StringUtils.equals(dependency.getClassifier(), artifact.getClassifier()) &&
                    StringUtils.equals(dependency.getType(), artifact.getType())) {
                result = true;
                break;
            }
        }
        return result;
    }

}
