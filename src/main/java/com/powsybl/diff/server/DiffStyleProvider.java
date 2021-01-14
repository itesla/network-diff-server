/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import com.powsybl.sld.library.ComponentSize;
import com.powsybl.sld.model.Edge;
import com.powsybl.sld.model.FeederNode;
import com.powsybl.sld.model.FeederType;
import com.powsybl.sld.model.Node;
import com.powsybl.sld.model.Node.NodeType;
import com.powsybl.sld.svg.DefaultDiagramStyleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.powsybl.sld.svg.DiagramStyles.escapeClassName;

/**
 *
 * @author Giovanni Ferrari <giovanni.ferrari@techrain.eu>
 */
public class DiffStyleProvider extends DefaultDiagramStyleProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiffStyleProvider.class);

    private static final String ARROW1 = ".ARROW1_";
    private static final String ARROW2 = ".ARROW2_";
    private static final String UP = "_UP";
    private static final String DOWN = "_DOWN";

    private static final String DIFF_COLOR = "red";
    private static final String DEFAULT_COLOR = "black";

    private List<String> vlDiffs;
    private List<String> branchDiffs;

    public DiffStyleProvider(List<String> vlDiffs, List<String> branchDiffs) {
        this.vlDiffs = Objects.requireNonNull(vlDiffs);
        this.branchDiffs = Objects.requireNonNull(branchDiffs);
    }

    @Override
    public Map<String, String> getSvgNodeStyleAttributes(Node node, ComponentSize size, String subComponentName, boolean isShowInternalNodes) {
        Map<String, String> style = super.getSvgNodeStyleAttributes(node, size, subComponentName, isShowInternalNodes);
        String nodeColor = DEFAULT_COLOR;
        if (NodeType.SWITCH.equals(node.getType()) && vlDiffs.contains(node.getId())) {
            nodeColor = DIFF_COLOR;
        }
        style.put("stroke", nodeColor);
        return style;
    }

    @Override
    public Map<String, String> getSvgWireStyleAttributes(Edge edge, boolean highlightLineState) {
        Map<String, String> style = super.getSvgWireStyleAttributes(edge, highlightLineState);
        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();
        String wireColor = DEFAULT_COLOR;
        if (branchDiffs.contains(node1.getId()) || branchDiffs.contains(node2.getId())) {
            wireColor = DIFF_COLOR;
        }
        style.put("stroke", wireColor);
        style.put("stroke-width", "1");
        return style;
    }

    @Override
    public Optional<String> getCssNodeStyleAttributes(Node node, boolean isShowInternalNodes) {
        Objects.requireNonNull(node);
        LOGGER.info("node: Id='{}', type='{}', componentType='{}', equipmentId='{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId());
        if (node instanceof FeederNode) {
            String arrow1Color = DEFAULT_COLOR;
            String arrow2Color = DEFAULT_COLOR;
            if (FeederType.BRANCH.equals(((FeederNode) node).getFeederType()) && branchDiffs.contains(node.getId())) {
                arrow1Color = DIFF_COLOR;
                arrow2Color = DIFF_COLOR;
            }
            StringBuilder style = new StringBuilder();
            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-up {stroke: " + arrow1Color + "; fill: " + arrow1Color + "; fill-opacity:1; visibility: visible;}");
            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-down { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-down {stroke: " + arrow1Color + "; fill: " + arrow1Color + "; fill-opacity:1;  visibility: visible;}");
            style.append(ARROW1).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-up { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-up {stroke: " + arrow2Color + "; fill: " + arrow2Color + "; fill-opacity:1; visibility: visible;}");
            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(UP).append(" .arrow-down { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-down {stroke: " + arrow2Color + "; fill: " + arrow2Color + "; fill-opacity:1;  visibility: visible;}");
            style.append(ARROW2).append(escapeClassName(node.getId()))
                 .append(DOWN).append(" .arrow-up { stroke-opacity:0; fill-opacity:0; visibility: hidden;}");

            return Optional.of(style.toString());
        }
        return super.getCssNodeStyleAttributes(node, isShowInternalNodes);
    }
}
