/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import com.powsybl.sld.library.ComponentLibrary;
import com.powsybl.sld.model.Edge;
import com.powsybl.sld.model.Node;
import com.powsybl.sld.svg.DefaultDiagramStyleProvider;
import com.powsybl.sld.svg.DiagramLabelProvider;
import com.powsybl.sld.svg.DiagramStyles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.sld.svg.DiagramStyles.CONSTANT_COLOR_CLASS;
import static com.powsybl.sld.svg.DiagramStyles.WIRE_STYLE_CLASS;

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

  /*  @Override
    public List<String> getSvgNodeStyles(Node node, ComponentLibrary componentLibrary, boolean showInternalNodes) {
        List<String> nodeStyles = super.getSvgNodeStyles(node, componentLibrary, showInternalNodes);
        List<String> styles = new ArrayList<>();

        if (Node.NodeType.SWITCH.equals(node.getType()) && switchDiffs.contains(node.getId())) {
            LOGGER.info("** switch $$$ node: Id='{}', type='{}', componentType='{}', equipmentId='{}', styles= '{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId(), nodeStyles);
            styles.add("sld-disconnector");
            styles.add("sld-constant-color-diff");
            styles.add(node.isOpen() ? DiagramStyles.OPEN_SWITCH_STYLE_CLASS : DiagramStyles.CLOSED_SWITCH_STYLE_CLASS);
            return styles;
        } else
        if ("TWO_WINDINGS_TRANSFORMER".equals(node.getComponentType()) && branchDiffs.contains(node.getId())) {
            LOGGER.info("** 2wt $$$ node: Id='{}', type='{}', componentType='{}', equipmentId='{}', styles= '{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId(), nodeStyles);
            return nodeStyles;
        }

        return nodeStyles;
    }*/

    @Override
    public List<String> getSvgNodeStyles(Node node, ComponentLibrary componentLibrary, boolean showInternalNodes) {
        List<String> nodeStyles = super.getSvgNodeStyles(node, componentLibrary, showInternalNodes);
        LOGGER.info("%%%%% node {}  componenttype {}", node.getId(), node.getComponentType());
        if (Node.NodeType.SWITCH.equals(node.getType()) && switchDiffs.contains(node.getId())) {
            LOGGER.info("** switch $$$ node: Id='{}', type='{}', componentType='{}', equipmentId='{}', styles= '{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId(), nodeStyles);
            Collections.replaceAll(nodeStyles, CONSTANT_COLOR_CLASS, CONSTANT_COLOR_CLASS+"-diff");
        } else
//        if ("TWO_WINDINGS_TRANSFORMER".equals(node.getComponentType()) && branchDiffs.contains(node.getId())) {
        if ("TWO_WINDINGS_TRANSFORMER".equals(node.getComponentType())) {
            LOGGER.info("** 2wt $$$ node: Id='{}', type='{}', componentType='{}', equipmentId='{}', styles= '{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId(), nodeStyles);
            Collections.replaceAll(nodeStyles, CONSTANT_COLOR_CLASS, CONSTANT_COLOR_CLASS+"-diff");
        }

        return nodeStyles;
    }


    @Override
    public List<String> getSvgNodeDecoratorStyles(DiagramLabelProvider.NodeDecorator nodeDecorator, Node node, ComponentLibrary componentLibrary) {
        List<String> nodeDecoratorStyles = super.getSvgNodeDecoratorStyles(nodeDecorator, node, componentLibrary);
        LOGGER.info("££ $$$ node: Id='{}', type='{}', componentType='{}', equipmentId='{}', styles= '{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId(), nodeDecoratorStyles);
        return nodeDecoratorStyles;
    }

/*
    @Override
    public List<String> getSvgWireStyles(Edge edge, boolean highlightLineState) {
        List<String> style = super.getSvgWireStyles(edge, highlightLineState);
        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();
        String wireColor = DEFAULT_COLOR;
        if (branchSideDiffs.contains(node1.getId()) || branchSideDiffs.contains(node2.getId())) {
            List<String> styles = new ArrayList<>();
            styles.add(WIRE_STYLE_CLASS);
            if (isConstantColor(edge)) {
                styles.add("sld-constant-color-diff");
            }
            getEdgeStyle(edge).ifPresent(styles::add);
            if (highlightLineState) {
                getHighlightLineStateStyle(edge).ifPresent(styles::add);
            }
            return styles;

        }
        return style;

    }
*/
@Override
public List<String> getSvgWireStyles(Edge edge, boolean highlightLineState) {
    List<String> style = super.getSvgWireStyles(edge, highlightLineState);
    Node node1 = edge.getNode1();
    Node node2 = edge.getNode2();
    String wireColor = DEFAULT_COLOR;
    if (branchSideDiffs.contains(node1.getId()) || branchSideDiffs.contains(node2.getId())) {
        LOGGER.info("** wire $$$ edge: Id1='{}', id2='{}', styles= '{}'", edge.getNode1().getId(), edge.getNode2().getId(), style);
        Collections.replaceAll(style, CONSTANT_COLOR_CLASS, CONSTANT_COLOR_CLASS+"-diff");
    }
    return style;

}

    @Override
    public List<String> getCssFilenames() {
        return Arrays.asList("tautologies.css", "diffs.css");
    }

    @Override
    public List<URL> getCssUrls() {
        return getCssFilenames().stream().map(n -> getClass().getResource("/" + n))
                .collect(Collectors.toList());
    }

    /*
    @Override
    public Map<String, String> getSvgNodeStyleAttributes(Node node, ComponentSize size, String subComponentName, boolean isShowInternalNodes) {
//        LOGGER.info("** node: Id='{}', type='{}', componentType='{}', equipmentId='{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId());
        Map<String, String> style = super.getSvgNodeStyleAttributes(node, size, subComponentName, isShowInternalNodes);
        String nodeColor = DEFAULT_COLOR;
        if (NodeType.SWITCH.equals(node.getType()) && switchDiffs.contains(node.getId())) {
            nodeColor = DIFF_COLOR;
        }
        if ("TWO_WINDINGS_TRANSFORMER".equals(node.getComponentType()) && branchDiffs.contains(node.getId())) {
            nodeColor = DIFF_COLOR;
        }
        style.put("stroke", nodeColor);
        return style;
    }
*/

/*
    @Override
    public Map<String, String> getSvgWireStyleAttributes(Edge edge, boolean highlightLineState) {
        Map<String, String> style = super.getSvgWireStyleAttributes(edge, highlightLineState);
        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();
        String wireColor = DEFAULT_COLOR;
        if (branchSideDiffs.contains(node1.getId()) || branchSideDiffs.contains(node2.getId())) {
            wireColor = DIFF_COLOR;
        }
        style.put("stroke", wireColor);
        style.put("stroke-width", "1");
        return style;
    }
*/

/*    @Override
    public Optional<String> getCssNodeStyleAttributes(Node node, boolean isShowInternalNodes) {
        Objects.requireNonNull(node);
//        LOGGER.info("node: Id='{}', type='{}', componentType='{}', equipmentId='{}'", node.getId(), node.getType(), node.getComponentType(), node.getEquipmentId());
        if (node instanceof FeederNode) {
            String arrow1Color = DEFAULT_COLOR;
            String arrow2Color = DEFAULT_COLOR;
            FeederType nodeFeederType = ((FeederNode) node).getFeederType();
            if ((FeederType.BRANCH.equals(nodeFeederType) || FeederType.TWO_WINDINGS_TRANSFORMER_LEG.equals(nodeFeederType))
                && branchSideDiffs.contains(node.getId())) {
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
    }*/
}
