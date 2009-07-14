/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.sgs.impl.service.nodemap.coordinator.affinity;

import java.util.Properties;

/**
 *
 * @author kbt
 */
public class LabelPropGroupFinder implements GroupFinder {

    private final AffinityGroupCoordinator coordinator;

    LabelPropGroupFinder(Properties properties,
                         AffinityGroupCoordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
