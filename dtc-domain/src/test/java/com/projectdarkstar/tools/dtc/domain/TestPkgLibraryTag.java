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

import com.bm.datagen.Generator;
import com.bm.datagen.annotations.GeneratorType;
import com.bm.datagen.relation.BeanCollectionGenerator;
import com.bm.testsuite.junit4.BaseEntityJunit4Fixture;
import java.util.List;

/**
 * Test the PkgLibraryTag entity
 */
public class TestPkgLibraryTag extends BaseEntityJunit4Fixture<PkgLibraryTag>
{
    public TestPkgLibraryTag() {
        super(PkgLibraryTag.class, new Generator[]{new LibraryGenerator()});
    }
    
    @GeneratorType(className = List.class, field="libraries")
    private static final class LibraryGenerator extends BeanCollectionGenerator<PkgLibrary> {
        private LibraryGenerator() {
            super(PkgLibrary.class, 10);
        }
    }
}
