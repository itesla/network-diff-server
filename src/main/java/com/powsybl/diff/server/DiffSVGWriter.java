package com.powsybl.diff.server;

import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.library.ComponentLibrary;
import com.powsybl.sld.library.ComponentMetadata;
import com.powsybl.sld.model.BusCell;
import com.powsybl.sld.model.FeederNode;
import com.powsybl.sld.svg.DefaultSVGWriter;
import com.powsybl.sld.svg.DiagramLabelProvider;
import com.powsybl.sld.svg.GraphMetadata;
import com.powsybl.sld.svg.InitialValue;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.powsybl.sld.library.ComponentTypeName.ARROW;
import static com.powsybl.sld.svg.DiagramStyles.*;

/**
 * @author Christian Biasuzzi <christian.biasuzzi@techrain.eu>
 */
public class DiffSVGWriter extends DefaultSVGWriter {

    private List<String> vlDiffs;
    private List<String> branchDiffs;

    public DiffSVGWriter(ComponentLibrary componentLibrary, LayoutParameters layoutParameters, List<String> vlDiffs, List<String> branchDiffs) {
        super(componentLibrary, layoutParameters);
        this.vlDiffs = Objects.requireNonNull(vlDiffs);
        this.branchDiffs = Objects.requireNonNull(branchDiffs);
    }

    @Override
    protected void insertArrowsAndLabels(String prefixId, String wireId, List<Double> points, Element root, FeederNode feederNode, GraphMetadata metadata, DiagramLabelProvider initProvider, boolean feederArrowSymmetry) {
        InitialValue init = initProvider.getInitialValue(feederNode);

        boolean arrowSymmetry = feederNode.getDirection() == BusCell.Direction.TOP || feederArrowSymmetry;

        Optional<String> label1 = arrowSymmetry ? init.getLabel1() : init.getLabel2();
        Optional<DiagramLabelProvider.Direction> direction1 = arrowSymmetry ? init.getArrowDirection1() : init.getArrowDirection2();

        Optional<String> label2 = arrowSymmetry ? init.getLabel2() : init.getLabel1();
        Optional<DiagramLabelProvider.Direction> direction2 = arrowSymmetry ? init.getArrowDirection2() : init.getArrowDirection1();

        int iArrow1 = arrowSymmetry ? 1 : 2;
        int iArrow2 = arrowSymmetry ? 2 : 1;

        //diff specific: if the diff list contains this feeder id
        final boolean diffStyleOverrides = branchDiffs.contains(feederNode.getId());

        // we draw the arrow only if value 1 is present
        label1.ifPresent(lb ->
                drawArrowAndLabel(prefixId, wireId, points, root, lb, init.getLabel3(), direction1, 0, iArrow1, metadata, diffStyleOverrides));

        // we draw the arrow only if value 2 is present
        label2.ifPresent(lb -> {
            double shiftArrow2 = 2 * metadata.getComponentMetadata(ARROW).getSize().getHeight();
            drawArrowAndLabel(prefixId, wireId, points, root, lb, init.getLabel4(),
                    direction2, shiftArrow2, iArrow2, metadata,
                    diffStyleOverrides);
        });
    }

    private void drawArrowAndLabel(String prefixId, String wireId, List<Double> points, Element root,
                                   String labelR, Optional<String> labelL, Optional<DiagramLabelProvider.Direction> dir, double shift, int iArrow,
                                   GraphMetadata metadata, boolean isdiffStyleOverrides) {
        ComponentMetadata cd = metadata.getComponentMetadata(ARROW);

        double shX = cd.getSize().getWidth() + LABEL_OFFSET;
        double shY = cd.getSize().getHeight() / 2;

        double y1 = points.get(1);
        double y2 = points.get(3);

        Element g = root.getOwnerDocument().createElement(GROUP);
        String arrowWireId = wireId + "_ARROW" + iArrow;
        g.setAttribute("id", arrowWireId);
        transformArrow(points, cd.getSize(), shift, g);

        insertArrowSVGIntoDocumentSVG(prefixId, g, y1 > y2 ? 180 : 0);
        Element label = createLabelElement(labelR, shX, shY, 0, g);
        g.appendChild(label);

        List<String> styles = new ArrayList<>(3);
        componentLibrary.getComponentStyleClass(ARROW).ifPresent(styles::add);

        //diff specific: set the style suffix
        String diffSuffix = isdiffStyleOverrides ? "-diff2" : "-diff1";
        styles.add(iArrow == 1 ? ARROW_ACTIVE_CLASS + diffSuffix : ARROW_REACTIVE_CLASS + diffSuffix);
        dir.ifPresent(direction -> styles.add(direction == DiagramLabelProvider.Direction.UP ? UP_CLASS : DOWN_CLASS));
        g.setAttribute(CLASS, String.join(" ", styles));

        labelL.ifPresent(s -> {
            Element labelLeft = createLabelElement(s, -LABEL_OFFSET, shY, 0, g);
            labelLeft.setAttribute(STYLE, "text-anchor:end");
            g.appendChild(labelLeft);
        });

        root.appendChild(g);
        metadata.addArrowMetadata(new GraphMetadata.ArrowMetadata(arrowWireId, wireId, layoutParameters.getArrowDistance()));

    }

}
