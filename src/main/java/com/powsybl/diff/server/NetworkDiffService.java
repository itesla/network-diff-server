/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.diff.*;
import com.powsybl.iidm.network.Branch;
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
import com.powsybl.sld.svg.DefaultSVGWriter;
import com.powsybl.sld.svg.DiagramLabelProvider;
import com.powsybl.sld.svg.DiagramStyleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

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

    public String diff(UUID network1Uuid, UUID network2Uuid, String vlId) {

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

        List<String> branches = vl1.getConnectableStream(Branch.class).map(Branch::getId).collect(Collectors.toList());

        DiffEquipment diffEquipment = new DiffEquipment();
        diffEquipment.setVoltageLevels(Collections.singletonList(vlId));
        List<DiffEquipmentType> equipmentTypes = new ArrayList<DiffEquipmentType>();
        equipmentTypes.add(DiffEquipmentType.VOLTAGE_LEVELS);
        if (!branches.isEmpty()) {
            equipmentTypes.add(DiffEquipmentType.BRANCHES);
            diffEquipment.setBranches(branches);
        }
        diffEquipment.setEquipmentTypes(equipmentTypes);

        NetworkDiff ndiff = new NetworkDiff(config);
        NetworkDiffResults diffVl = ndiff.diff(network1, network2, diffEquipment);

        String jsonDiff = NetworkDiff.writeJson(diffVl);

        //NaN is not part of the JSON standard and frontend would fail when parsing it
        //it should be handled at the source, though
        jsonDiff = jsonDiff.replace(": NaN,", ": \"Nan\",");

        LOGGER.info("network1 uuid: {}, network2 uuid: {}, vl: {}, diff: {}", network1Uuid, network2Uuid, vlId, jsonDiff);
        return jsonDiff;
    }

    public String getVoltageLevelSvg(UUID networkUuid, String vl, List<String> diffs) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid);
            return writeSVG(network, vl, diffs, Collections.emptyList());
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    private String writeSVG(Network network, String vlId, List<String> vlDiffs, List<String> branchDiffs) {
        String svgData;
        String metadataData;
        String jsonData;
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter();
             StringWriter jsonWriter = new StringWriter()) {
            DiagramStyleProvider styleProvider = new DiffStyleProvider(vlDiffs, branchDiffs);
            LayoutParameters layoutParameters = new LayoutParameters();
            ComponentLibrary componentLibrary = new ResourcesComponentLibrary("/ConvergenceLibrary");

            DiagramLabelProvider initProvider = new DiffDiagramLabelProvider(network, componentLibrary, layoutParameters);
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

    public String getVoltageLevelSvg2(UUID network1Uuid, UUID network2Uuid, String vlId) {
        try {
            Network network = networkStoreService.getNetwork(network1Uuid);

            String diffVls = diff(network1Uuid, network2Uuid, vlId);
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> jsonMap = objectMapper.readValue(diffVls,
                    new TypeReference<Map<String, Object>>() { });
            List<String> switchesDiff = (List<String>) ((Map) ((List) (jsonMap.get("diff.VoltageLevels"))).get(0)).get("vl.switchesStatus-delta");
            List<String> branchesDiff = (List<String>) ((ArrayList) jsonMap.get("diff.Branches")).stream().map(t -> ((Map) t).get("branch.terminalStatus-delta"))
                    .flatMap(t -> ((ArrayList<String>) t).stream()).collect(Collectors.toList());
            LOGGER.info("voltageLevelSvg2 - network1={}, network2={}, vl={}, switchesDiff: {}, branchesDiff: {}", network1Uuid, network2Uuid, vlId, switchesDiff, branchesDiff);

            return writeSVG(network, vlId, switchesDiff, branchesDiff);
        } catch (PowsyblException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

}
