/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import java.util.List;
import java.util.Map;

import com.powsybl.iidm.network.Network;
import com.powsybl.sld.library.ComponentSize;
import com.powsybl.sld.model.Node;
import com.powsybl.sld.util.TopologicalStyleProvider;

/**
 *
 * @author Giovanni Ferrari <giovanni.ferrari@techrain.eu>
 */
public class DiffStyleProvider extends TopologicalStyleProvider {

    private List<String> diffs;

    public DiffStyleProvider(Network network, List<String> diffs) {
        super(network);
        this.diffs = diffs;
    }

    @Override
    public Map<String, String> getSvgNodeStyleAttributes(Node node, ComponentSize size, String subComponentName, boolean isShowInternalNodes) {
        Map<String, String> style = super.getSvgNodeStyleAttributes(node, size, subComponentName, isShowInternalNodes);
        if (diffs.contains(node.getId())) {
            style.put("stroke", "red");
        }
        return style;
    }

}
