package com.powsybl.diff.server;

import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.library.ComponentLibrary;
import com.powsybl.sld.model.FeederNode;
import com.powsybl.sld.svg.DefaultSVGWriter;
import com.powsybl.sld.svg.DiagramLabelProvider;
import com.powsybl.sld.svg.GraphMetadata;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.powsybl.sld.svg.DiagramStyles.ARROW_ACTIVE_CLASS;
import static com.powsybl.sld.svg.DiagramStyles.ARROW_REACTIVE_CLASS;

/**
 * @author Christian Biasuzzi <christian.biasuzzi@techrain.eu>
 */
public class DiffSVGWriter extends DefaultSVGWriter {

    public static final String DIFFERENT_SUFFIX = "-diff1";
    public static final String SAME_SUFFIX = "-diff2";

    private final List<String> vlDiffs;
    private final List<String> branchDiffs;

    public DiffSVGWriter(ComponentLibrary componentLibrary, LayoutParameters layoutParameters, List<String> vlDiffs, List<String> branchDiffs) {
        super(componentLibrary, layoutParameters);
        this.vlDiffs = Objects.requireNonNull(vlDiffs);
        this.branchDiffs = Objects.requireNonNull(branchDiffs);
    }

    @Override
    protected void insertArrowsAndLabels(String prefixId, String wireId, List<Double> points, Element root, FeederNode feederNode, GraphMetadata metadata, DiagramLabelProvider initProvider, boolean feederArrowSymmetry) {
        super.insertArrowsAndLabels(prefixId, wireId, points, root, feederNode, metadata, initProvider, feederArrowSymmetry);

        //replace arrows css classes with the diff ones
        String diffSuffix = branchDiffs.contains(feederNode.getId()) ? SAME_SUFFIX : DIFFERENT_SUFFIX;
        for (int i = 0; i < root.getElementsByTagName(GROUP).getLength(); i++) {
            Node gNode = root.getElementsByTagName(GROUP).item(i);
            if (gNode instanceof Element) {
                List<String> classesList = Arrays.asList(((Element) gNode).getAttribute(CLASS).split(" "));
                Collections.replaceAll(classesList, ARROW_ACTIVE_CLASS, ARROW_ACTIVE_CLASS + diffSuffix);
                Collections.replaceAll(classesList, ARROW_REACTIVE_CLASS, ARROW_REACTIVE_CLASS + diffSuffix);
                ((Element) gNode).setAttribute(CLASS, String.join(" ", classesList));
            }
        }
    }
}
