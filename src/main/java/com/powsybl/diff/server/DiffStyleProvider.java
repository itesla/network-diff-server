/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import com.powsybl.sld.library.ComponentLibrary;
import com.powsybl.sld.library.ComponentTypeName;
import com.powsybl.sld.model.Edge;
import com.powsybl.sld.model.Node;
import com.powsybl.sld.svg.DefaultDiagramStyleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.sld.svg.DiagramStyles.CONSTANT_COLOR_CLASS;

/**
 * @author Giovanni Ferrari <giovanni.ferrari@techrain.eu>
 */
public class DiffStyleProvider extends DefaultDiagramStyleProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiffStyleProvider.class);

    private List<String> switchDiffs;
    private List<String> branchSideDiffs;
    private List<String> branchDiffs;

    public DiffStyleProvider(List<String> switchDiffs, List<String> branchSideDiffs, List<String> branchDiffs) {
        this.switchDiffs = Objects.requireNonNull(switchDiffs);
        this.branchSideDiffs = Objects.requireNonNull(branchSideDiffs);
        this.branchDiffs = Objects.requireNonNull(branchDiffs);
        LOGGER.info("££ switchDiffs: {}", branchDiffs);
        LOGGER.info("££ branchSideDiffs: {}", branchSideDiffs);
        LOGGER.info("££ branchDiffs: {}", branchDiffs);
    }

    @Override
    public List<String> getSvgNodeStyles(Node node, ComponentLibrary componentLibrary, boolean showInternalNodes) {
        List<String> nodeStyles = super.getSvgNodeStyles(node, componentLibrary, showInternalNodes);
        LOGGER.debug("node before: id {} node_type {} componenttype {}, styles {}", node.getId(), node.getType(), node.getComponentType(), nodeStyles);
        Collections.replaceAll(nodeStyles, CONSTANT_COLOR_CLASS, CONSTANT_COLOR_CLASS + "-diff1");

        if (Node.NodeType.SWITCH.equals(node.getType()) && switchDiffs.contains(node.getId())) {
            Collections.replaceAll(nodeStyles, CONSTANT_COLOR_CLASS + "-diff1", CONSTANT_COLOR_CLASS + "-diff2");
        } else if (ComponentTypeName.TWO_WINDINGS_TRANSFORMER.equals(node.getComponentType())) {
            List<String> edgesNodesIds = node.getAdjacentEdges().stream()
                    .map(e -> e.getNodes().stream().map(n -> n.getId())
                            .filter(nId -> !nId.equals(node.getId()))
                            .collect(Collectors.toList()))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            if (branchDiffs.containsAll(edgesNodesIds)) {
                // LOGGER.info("** 2wt $$$ node: Id='{}', type='{}', componentType='{}', equipmentId='{}', styles= '{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId(), nodeStyles);
                Collections.replaceAll(nodeStyles, CONSTANT_COLOR_CLASS + "-diff1", CONSTANT_COLOR_CLASS + "-diff2");
            }
        }

        LOGGER.debug("node after: id {} node_type {} componenttype {}, styles {}", node.getId(), node.getType(), node.getComponentType(), nodeStyles);
        return nodeStyles;
    }

    @Override
    public List<String> getSvgWireStyles(Edge edge, boolean highlightLineState) {
        List<String> style = super.getSvgWireStyles(edge, highlightLineState);
        LOGGER.debug("edge before: Id1='{}', id2='{}', styles= '{}'", edge.getNode1().getId(), edge.getNode2().getId(), style);
        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();
        if (branchDiffs.contains(node1.getId()) || branchDiffs.contains(node2.getId())) {
            Collections.replaceAll(style, CONSTANT_COLOR_CLASS, CONSTANT_COLOR_CLASS + "-diff2");
        } else {
            Collections.replaceAll(style, CONSTANT_COLOR_CLASS, CONSTANT_COLOR_CLASS + "-diff1");
        }
        LOGGER.debug("edge after:  Id1='{}', id2='{}', styles= '{}'", edge.getNode1().getId(), edge.getNode2().getId(), style);
        return style;
    }

    @Override
    public List<String> getCssFilenames() {
        return Stream.concat(super.getCssFilenames().stream(), Stream.of("diffs.css")).collect(Collectors.toList());
    }
}
