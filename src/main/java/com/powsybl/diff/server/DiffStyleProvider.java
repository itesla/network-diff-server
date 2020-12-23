/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import static com.powsybl.sld.svg.DiagramStyles.escapeClassName;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.powsybl.iidm.network.Network;
import com.powsybl.sld.library.ComponentSize;
import com.powsybl.sld.model.FeederNode;
import com.powsybl.sld.model.FeederType;
import com.powsybl.sld.model.Node;
import com.powsybl.sld.model.Node.NodeType;
import com.powsybl.sld.util.TopologicalStyleProvider;

/**
 *
 * @author Giovanni Ferrari <giovanni.ferrari@techrain.eu>
 */
public class DiffStyleProvider extends TopologicalStyleProvider {

    private static final String ARROW1 = ".ARROW1_";
    private static final String ARROW2 = ".ARROW2_";
    private static final String UP = "_UP";
    private static final String DOWN = "_DOWN";
    private static final String ARROW1_COLOR = "red";
    private static final String ARROW2_COLOR = "orange";

    private List<String> vlDiffs;
    private List<String> branchDiffs;

    public DiffStyleProvider(Network network, List<String> vlDiffs, List<String> branchDiffs) {
        super(network);
        this.vlDiffs = Objects.requireNonNull(vlDiffs);
        this.branchDiffs = Objects.requireNonNull(branchDiffs);
    }

    @Override
    public Map<String, String> getSvgNodeStyleAttributes(Node node, ComponentSize size, String subComponentName, boolean isShowInternalNodes) {
        Map<String, String> style = super.getSvgNodeStyleAttributes(node, size, subComponentName, isShowInternalNodes);
        if (NodeType.SWITCH.equals(node.getType()) && vlDiffs.contains(node.getId())) {
            style.put("stroke", "red");
        }
        return style;
    }

    @Override
    public Optional<String> getCssNodeStyleAttributes(Node node, boolean isShowInternalNodes) {
        Objects.requireNonNull(node);
        System.out.println("*** '" + node.getId() + "' " + node.getType() + " - " + node.getComponentType() + " - '" + node.getEquipmentId() + "'");
        if (node instanceof FeederNode && FeederType.BRANCH.equals(((FeederNode) node).getFeederType())
            && branchDiffs.contains(node.getId())) {
            StringBuilder style = new StringBuilder();
            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-up {stroke: " + ARROW1_COLOR + "; fill: " + ARROW1_COLOR + "; fill-opacity:1; visibility: visible;}");
            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-down { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-down {stroke: " + ARROW1_COLOR + "; fill: " + ARROW1_COLOR + "; fill-opacity:1;  visibility: visible;}");
            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-up { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-up {stroke: " + ARROW2_COLOR + "; fill: " + ARROW2_COLOR + "; fill-opacity:1; visibility: visible;}");
            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-down { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-down {stroke: " + ARROW2_COLOR + "; fill: " + ARROW2_COLOR + "; fill-opacity:1;  visibility: visible;}");
            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-up { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            return Optional.of(style.toString());
        }
        return super.getCssNodeStyleAttributes(node, isShowInternalNodes);
    }
}
