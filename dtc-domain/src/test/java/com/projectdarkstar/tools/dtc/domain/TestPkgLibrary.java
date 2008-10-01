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
 * Test the PkgLibrary entity
 */
public class TestPkgLibrary extends BaseEntityJunit4Fixture<PkgLibrary>
{
    public TestPkgLibrary() {
        super(PkgLibrary.class, new Generator[]{new TagGenerator(), new FileGenerator()});
    }
    
    @GeneratorType(className = List.class, field="tags")
    private static final class TagGenerator extends BeanCollectionGenerator<PkgLibraryTag> {
        private TagGenerator() {
            super(PkgLibraryTag.class, 10);
        }
    }
    
    @GeneratorType(className = byte[].class, field="file")
    private static final class FileGenerator implements Generator<byte[]> {
        private final byte[] file = new byte[]{0,1,2,3,4};
        private FileGenerator() {}
        public byte[] getValue() {
            return file;
        }
    }
}
