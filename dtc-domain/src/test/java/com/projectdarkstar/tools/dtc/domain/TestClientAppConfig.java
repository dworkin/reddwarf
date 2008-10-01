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
import com.bm.datagen.relation.SingleBeanGenerator;
import com.bm.testsuite.junit4.BaseEntityJunit4Fixture;
import java.util.List;
import org.junit.Ignore;

/**
 * Test for ClientAppConfig entity
 */
@Ignore
public class TestClientAppConfig extends BaseEntityJunit4Fixture<ClientAppConfig>
{
    public TestClientAppConfig() {
        super(ClientAppConfig.class, new Generator[]{new PropertyGenerator(), new ClientAppGenerator()});
    }
    
    @GeneratorType(className = List.class, field="properties")
    private static final class PropertyGenerator extends BeanCollectionGenerator<Property> {
        private PropertyGenerator() {
            super(Property.class, 10);
        }
    }
    
    @GeneratorType(className = ClientApp.class, field="clientApp")
    private static final class ClientAppGenerator extends SingleBeanGenerator<ClientApp> {
        private ClientAppGenerator() {
            super(ClientApp.class, new Generator[]{new PkgLibraryGenerator()});
        }
    }
    
    @GeneratorType(className = PkgLibrary.class, field="requiredPkg")
    private static final class PkgLibraryGenerator extends SingleBeanGenerator<PkgLibrary> {
        private PkgLibraryGenerator() {
            super(PkgLibrary.class);
        }
    }

}
