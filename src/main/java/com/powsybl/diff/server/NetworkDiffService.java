/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.diff.DiffConfig;
import com.powsybl.iidm.diff.DiffEquipment;
import com.powsybl.iidm.diff.DiffEquipmentType;
import com.powsybl.iidm.diff.NetworkDiff;
import com.powsybl.iidm.diff.NetworkDiffResults;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.sld.GraphBuilder;
import com.powsybl.sld.NetworkGraphBuilder;
import com.powsybl.sld.VoltageLevelDiagram;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.layout.SmartVoltageLevelLayoutFactory;
import com.powsybl.sld.library.ComponentLibrary;
import com.powsybl.sld.library.ResourcesComponentLibrary;
import com.powsybl.sld.svg.DefaultDiagramLabelProvider;
import com.powsybl.sld.svg.DefaultSVGWriter;
import com.powsybl.sld.svg.DiagramLabelProvider;
import com.powsybl.sld.svg.DiagramStyleProvider;

/**
 * @author Christian Biasuzzi <christian.biasuzzi@techrain.eu>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
class NetworkDiffService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkDiffService.class);

    @Autowired
    private NetworkStoreService networkStoreService;

    DiffConfig config = new DiffConfig(DiffConfig.EPSILON_DEFAULT, DiffConfig.FILTER_DIFF_DEFAULT);

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    Map<UUID, String> getNetworkIds() {
        return networkStoreService.getNetworkIds();
    }

    String diff(UUID network1Uuid, UUID network2Uuid, String vlId) {

        NetworkDiff ndiff = new NetworkDiff(config);
        DiffEquipment diffEquipment = new DiffEquipment();
        diffEquipment.setEquipmentTypes(Collections.singletonList(DiffEquipmentType.VOLTAGE_LEVELS));

        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);

        VoltageLevel vl1 = network1.getVoltageLevel(vlId);
        if (vl1 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voltage level " + vlId + " not found in network " + network1Uuid);
        }

        VoltageLevel vl2 = network2.getVoltageLevel(vlId);
        if (vl2 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voltage level " + vlId + " not found in network " + network2Uuid);
        }

        diffEquipment.setVoltageLevels(Collections.singletonList(vlId));
        NetworkDiffResults diffVl = ndiff.diff(network1, network2, diffEquipment);

        String jsonDiff = NetworkDiff.writeJson(diffVl);

        LOGGER.info("network1 uuid: {}, network2 uuid: {}, vl: {}, diff: {}", network1Uuid, network2Uuid, vlId, jsonDiff);

        return jsonDiff;
    }

    public String getVoltageLevelSvg(UUID networkUuid, String vl, List<String> diffs) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid);
            return writeSVG(network, vl, diffs);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    private String writeSVG(Network network, String vlId, List<String> diffs) {
        String svgData;
        String metadataData;
        String jsonData;
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter();
             StringWriter jsonWriter = new StringWriter()) {
            DiagramStyleProvider styleProvider = new DiffStyleProvider(network, diffs);
            LayoutParameters layoutParameters = new LayoutParameters();
            ComponentLibrary componentLibrary = new ResourcesComponentLibrary("/ConvergenceLibrary");

            DiagramLabelProvider initProvider = new DefaultDiagramLabelProvider(network, componentLibrary, layoutParameters);
            GraphBuilder graphBuilder = new NetworkGraphBuilder(network);

            VoltageLevelDiagram diagram = VoltageLevelDiagram.build(graphBuilder, vlId, new SmartVoltageLevelLayoutFactory(network), false);
            diagram.writeSvg("",
                    new DefaultSVGWriter(componentLibrary, layoutParameters),
                    initProvider,
                    styleProvider,
                    svgWriter,
                    metadataWriter);
            diagram.getGraph().writeJson(jsonWriter);
            svgWriter.flush();
            metadataWriter.flush();
            svgData = svgWriter.toString();
            metadataData = metadataWriter.toString();
            jsonData = jsonWriter.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return svgData;
    }
}
