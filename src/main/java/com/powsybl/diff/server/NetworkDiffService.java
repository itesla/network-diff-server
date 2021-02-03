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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.diff.DiffConfig;
import com.powsybl.iidm.diff.DiffEquipment;
import com.powsybl.iidm.diff.DiffEquipmentType;
import com.powsybl.iidm.diff.NetworkDiff;
import com.powsybl.iidm.diff.NetworkDiffResults;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.sld.GraphBuilder;
import com.powsybl.sld.NetworkGraphBuilder;
import com.powsybl.sld.SubstationDiagram;
import com.powsybl.sld.VoltageLevelDiagram;
import com.powsybl.sld.layout.HorizontalSubstationLayoutFactory;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.layout.SmartVoltageLevelLayoutFactory;
import com.powsybl.sld.library.ComponentLibrary;
import com.powsybl.sld.library.ResourcesComponentLibrary;
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

    public String diff(UUID network1Uuid, UUID network2Uuid, String vlId) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(vlId);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);
        return diff(network1Uuid, network1, network2Uuid, network2, vlId);
    }

    private String diff(UUID network1Uuid, Network network1, UUID network2Uuid, Network network2, String vlId) {
        VoltageLevel vl1 = network1.getVoltageLevel(vlId);
        if (vl1 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voltage level " + vlId + " not found in network " + network1Uuid);
        }
        VoltageLevel vl2 = network2.getVoltageLevel(vlId);
        if (vl2 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voltage level " + vlId + " not found in network " + network2Uuid);
        }
        List<String> voltageLevels = Collections.singletonList(vlId);
        List<String> branches = vl1.getConnectableStream(Branch.class).map(Branch::getId).collect(Collectors.toList());
        String jsonDiff = diff(network1, network2, voltageLevels, branches);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, vl: {}, diff: {}", network1Uuid, network2Uuid, vlId, jsonDiff);
        return jsonDiff;
    }

    private String diff(Network network1, Network network2, List<String> voltageLevels, List<String> branches) {
        DiffEquipment diffEquipment = new DiffEquipment();
        diffEquipment.setVoltageLevels(voltageLevels);
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
        return jsonDiff;
    }

    public String getVoltageLevelSvg(UUID networkUuid, String vl, List<String> diffs) {
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(vl);
        Objects.requireNonNull(diffs);
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

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(vlId);
        try {
            Network network1 = getNetwork(network1Uuid);
            Network network2 = getNetwork(network2Uuid);
            String jsonDiff = diff(network1Uuid, network1, network2Uuid, network2, vlId);
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> jsonMap = objectMapper.readValue(jsonDiff, new TypeReference<Map<String, Object>>() { });
            List<String> switchesDiff = (List<String>) ((List) jsonMap.get("diff.VoltageLevels")).stream()
                    .map(t -> ((Map) t).get("vl.switchesStatus-delta"))
                    .flatMap(t -> ((List<String>) t).stream())
                    .collect(Collectors.toList());
            List<String> branchesDiff = (List<String>) ((List) jsonMap.get("diff.Branches")).stream()
                    .map(t -> ((Map) t).get("branch.terminalStatus-delta"))
                    .flatMap(t -> ((List<String>) t).stream())
                    .collect(Collectors.toList());
            LOGGER.info("network1={}, network2={}, vl={}, switchesDiff: {}, branchesDiff: {}", network1Uuid, network2Uuid, vlId, switchesDiff, branchesDiff);
            return writeSVG(network1, vlId, switchesDiff, branchesDiff);
        } catch (PowsyblException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    public String diffSubstation(UUID network1Uuid, UUID network2Uuid, String substationId) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(substationId);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);
        return diffSubstation(network1Uuid, network1, network2Uuid, network2, substationId);
    }

    private String diffSubstation(UUID network1Uuid, Network network1, UUID network2Uuid, Network network2, String substationId) {
        Substation substation1 = network1.getSubstation(substationId);
        if (substation1 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Substation " + substationId + " not found in network " + network1Uuid);
        }
        Substation substation2 = network2.getSubstation(substationId);
        if (substation2 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Substation " + substationId + " not found in network " + network2Uuid);
        }
        List<String> voltageLevels = substation1.getVoltageLevelStream().map(VoltageLevel::getId).collect(Collectors.toList());
        List<String> branches = substation1.getVoltageLevelStream().flatMap(vl -> vl.getConnectableStream(Line.class)).map(Line::getId).collect(Collectors.toList());
        List<String> twts = substation1.getTwoWindingsTransformerStream().map(TwoWindingsTransformer::getId).collect(Collectors.toList());
        branches.addAll(twts);
        String jsonDiff = diff(network1, network2, voltageLevels, branches);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, substation: {}, diff: {}", network1Uuid, network2Uuid, substationId, jsonDiff);
        return jsonDiff;
    }

    private String writesubstationSVG(Network network, String substationId, List<String> vlDiffs, List<String> branchDiffs) {
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
            SubstationDiagram diagram = SubstationDiagram.build(graphBuilder, substationId, new HorizontalSubstationLayoutFactory(),
                    new SmartVoltageLevelLayoutFactory(network), false);
            diagram.writeSvg("",
                    new DefaultSVGWriter(componentLibrary, layoutParameters),
                    initProvider,
                    styleProvider,
                    svgWriter,
                    metadataWriter);
            diagram.getSubGraph().writeJson(jsonWriter);
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

    public String getSubstationSvgDiff(UUID network1Uuid, UUID network2Uuid, String substationId) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(substationId);
        try {
            Network network1 = getNetwork(network1Uuid);
            Network network2 = getNetwork(network2Uuid);
            String jsonDiff = diffSubstation(network1Uuid, network1, network2Uuid, network2, substationId);
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> jsonMap = objectMapper.readValue(jsonDiff, new TypeReference<Map<String, Object>>() { });
            List<String> switchesDiff = (List<String>) ((List) jsonMap.get("diff.VoltageLevels")).stream()
                    .map(t -> ((Map) t).get("vl.switchesStatus-delta"))
                    .flatMap(t -> ((List<String>) t).stream())
                    .collect(Collectors.toList());
            List<String> branchesDiff = (List<String>) ((List) jsonMap.get("diff.Branches")).stream()
                    .map(t -> ((Map) t).get("branch.terminalStatus-delta"))
                    .flatMap(t -> ((List<String>) t).stream())
                    .collect(Collectors.toList());
            LOGGER.info("network1={}, network2={}, substation={}, switchesDiff: {}, branchesDiff: {}", network1Uuid, network2Uuid, substationId, switchesDiff, branchesDiff);
            return writesubstationSVG(network1, substationId, switchesDiff, branchesDiff);
        } catch (PowsyblException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
