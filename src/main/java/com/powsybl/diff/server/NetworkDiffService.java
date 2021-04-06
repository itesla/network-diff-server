/**
 * Copyright (c) 2020-2021, RTE (http://www.rte-france.com)
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
import com.powsybl.sld.svg.DiagramLabelProvider;

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

    class DiffData {
        final List<String> switchesDiff;
        final List<String> branchesDiff;

        DiffData(String jsonDiff) throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> jsonMap = objectMapper.readValue(jsonDiff, new TypeReference<Map<String, Object>>() { });
            switchesDiff = (List<String>) ((List) jsonMap.get("diff.VoltageLevels")).stream()
                    .map(t -> ((Map) t).get("vl.switchesStatus-delta"))
                    .flatMap(t -> ((List<String>) t).stream())
                    .collect(Collectors.toList());
            branchesDiff = (List<String>) ((List) jsonMap.get("diff.Branches")).stream()
                    .map(t -> ((Map) t).get("branch.terminalStatus-delta"))
                    .flatMap(t -> ((List<String>) t).stream())
                    .collect(Collectors.toList());
        }

        public List<String> getSwitchesIds() {
            return switchesDiff;
        }

        public List<String> getBranchesIds() {
            return branchesDiff;
        }

    }

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

    //voltage levels
    public String diffVoltageLevel(UUID network1Uuid, UUID network2Uuid, String vlId) {
        return diffVoltageLevel(network1Uuid, network2Uuid, vlId, DiffConfig.EPSILON_DEFAULT);
    }

    public String diffVoltageLevel(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(vlId);
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
        String jsonDiff = diffVoltageLevel(network1, network2, vlId, epsilon);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, vl: {}, diff: {}", network1Uuid, network2Uuid, vlId, jsonDiff);
        return jsonDiff;
    }

    private String diffVoltageLevel(Network network1, Network network2, String vlId, double epsilon) {
        List<String> voltageLevels = Collections.singletonList(vlId);
        List<String> branches = network1.getVoltageLevel(vlId).getConnectableStream(Branch.class).map(Branch::getId).collect(Collectors.toList());
        return diffVoltageLevels(network1, network2, voltageLevels, branches, epsilon);
    }

    private String diffVoltageLevels(Network network1, Network network2, List<String> voltageLevels, List<String> branches) {
        return diffVoltageLevels(network1, network2, voltageLevels, branches, DiffConfig.EPSILON_DEFAULT);
    }

    private String diffVoltageLevels(Network network1, Network network2, List<String> voltageLevels, List<String> branches, double epsilon) {
        DiffEquipment diffEquipment = new DiffEquipment();
        diffEquipment.setVoltageLevels(voltageLevels);
        List<DiffEquipmentType> equipmentTypes = new ArrayList<DiffEquipmentType>();
        equipmentTypes.add(DiffEquipmentType.VOLTAGE_LEVELS);
        if (!branches.isEmpty()) {
            equipmentTypes.add(DiffEquipmentType.BRANCHES);
            diffEquipment.setBranches(branches);
        }
        diffEquipment.setEquipmentTypes(equipmentTypes);
        NetworkDiff ndiff = new NetworkDiff(new DiffConfig(epsilon, DiffConfig.FILTER_DIFF_DEFAULT));
        NetworkDiffResults diffVl = ndiff.diff(network1, network2, diffEquipment);
        String jsonDiff = NetworkDiff.writeJson(diffVl);
        //NaN is not part of the JSON standard and frontend would fail when parsing it
        //it should be handled at the source, though
        jsonDiff = jsonDiff.replace(": NaN,", ": \"Nan\",");
        return jsonDiff;
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId) {
        return getVoltageLevelSvgDiff(network1Uuid, network2Uuid, vlId, DiffConfig.EPSILON_DEFAULT);
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(vlId);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);
        return getVoltageLevelSvgDiff(network1, network2, vlId, epsilon);
    }

    private String getVoltageLevelSvgDiff(Network network1, Network network2, String vlId, double epsilon) {
        Objects.requireNonNull(network1);
        Objects.requireNonNull(network2);
        Objects.requireNonNull(vlId);
        try {
            String jsonDiff = diffVoltageLevel(network1, network2, vlId, epsilon);
            DiffData diffData = new DiffData(jsonDiff);
            List<String> switchesDiff = diffData.getSwitchesIds();
            List<String> branchesDiff = diffData.getBranchesIds();
            LOGGER.info("switchesDiff: {}, branchesDiff: {}", switchesDiff, branchesDiff);
            return writeVoltageLevelSvg(network1, vlId, switchesDiff, branchesDiff);
        } catch (PowsyblException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String writeVoltageLevelSvg(Network network, String vlId, List<String> vlDiffs, List<String> branchDiffs) {
        String svgData;
        String metadataData;
        String jsonData;
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter();
             StringWriter jsonWriter = new StringWriter()) {
            ArrowsStyleProvider styleProvider = new DiffStyleProvider(vlDiffs, vlDiffs, branchDiffs);
            LayoutParameters layoutParameters = new LayoutParameters();
            layoutParameters.setCssInternal(true);
            ComponentLibrary componentLibrary = new ResourcesComponentLibrary("/ConvergenceLibrary");
            DiagramLabelProvider initProvider = new DiffDiagramLabelProvider(network, componentLibrary, layoutParameters);
            GraphBuilder graphBuilder = new NetworkGraphBuilder(network);
            VoltageLevelDiagram diagram = VoltageLevelDiagram.build(graphBuilder, vlId, new SmartVoltageLevelLayoutFactory(network), false);
            diagram.writeSvg("",
                    new DiffSVGWriter(componentLibrary, layoutParameters, styleProvider),
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

    //substations
    public String getSubstationSvgDiff(UUID network1Uuid, UUID network2Uuid, String substationId) {
        return getSubstationSvgDiff(network1Uuid, network2Uuid, substationId, DiffConfig.EPSILON_DEFAULT);
    }

    public String getSubstationSvgDiff(UUID network1Uuid, UUID network2Uuid, String substationId, double epsilon) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(substationId);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);
        return getSubstationSvgDiff(network1, network2, substationId, epsilon);
    }

    public String getSubstationSvgDiff(Network network1, Network network2, String substationId, double epsilon) {
        Objects.requireNonNull(network1);
        Objects.requireNonNull(network2);
        Objects.requireNonNull(substationId);
        try {
            String jsonDiff = diffSubstation(network1, network2, substationId, epsilon);
            DiffData diffData = new DiffData(jsonDiff);
            List<String> switchesDiff = diffData.getSwitchesIds();
            List<String> branchesDiff = diffData.getBranchesIds();
            LOGGER.info("switchesDiff: {}, branchesDiff: {}", switchesDiff, branchesDiff);
            return writeSubstationSvg(network1, substationId, switchesDiff, branchesDiff);
        } catch (PowsyblException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String writeSubstationSvg(Network network, String substationId, List<String> vlDiffs, List<String> branchDiffs) {
        String svgData;
        String metadataData;
        String jsonData;
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter();
             StringWriter jsonWriter = new StringWriter()) {
            ArrowsStyleProvider styleProvider = new DiffStyleProvider(vlDiffs, vlDiffs, branchDiffs);
            LayoutParameters layoutParameters = new LayoutParameters();
            layoutParameters.setCssInternal(true);
            ComponentLibrary componentLibrary = new ResourcesComponentLibrary("/ConvergenceLibrary");
            DiagramLabelProvider initProvider = new DiffDiagramLabelProvider(network, componentLibrary, layoutParameters);
            GraphBuilder graphBuilder = new NetworkGraphBuilder(network);
            SubstationDiagram diagram = SubstationDiagram.build(graphBuilder, substationId, new HorizontalSubstationLayoutFactory(),
                    new SmartVoltageLevelLayoutFactory(network), false);
            diagram.writeSvg("",
                    new DiffSVGWriter(componentLibrary, layoutParameters, styleProvider),
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

    public String diffSubstation(UUID network1Uuid, UUID network2Uuid, String substationId) {
        return diffSubstation(network1Uuid, network2Uuid, substationId, DiffConfig.EPSILON_DEFAULT);
    }

    public String diffSubstation(UUID network1Uuid, UUID network2Uuid, String substationId, double epsilon) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(substationId);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);
        Substation substation1 = network1.getSubstation(substationId);
        if (substation1 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Substation " + substationId + " not found in network1 " + network1Uuid);
        }
        Substation substation2 = network2.getSubstation(substationId);
        if (substation2 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Substation " + substationId + " not found in network2 " + network2Uuid);
        }
        String jsonDiff = diffSubstation(network1, network2, substationId, epsilon);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, substation: {}, diff: {}", network1Uuid, network2Uuid, substationId, jsonDiff);
        return jsonDiff;
    }

    private String diffSubstation(Network network1, Network network2, String substationId) {
        return diffSubstation(network1, network2, substationId, DiffConfig.EPSILON_DEFAULT);
    }

    private String diffSubstation(Network network1, Network network2, String substationId, double epsilon) {
        Substation substation1 = network1.getSubstation(substationId);
        List<String> voltageLevels = substation1.getVoltageLevelStream().map(VoltageLevel::getId)
                .collect(Collectors.toList());
        List<String> branches = substation1.getVoltageLevelStream().flatMap(vl -> vl.getConnectableStream(Line.class))
                .map(Line::getId).collect(Collectors.toList());
        List<String> twts = substation1.getTwoWindingsTransformerStream().map(TwoWindingsTransformer::getId)
                .collect(Collectors.toList());
        branches.addAll(twts);
        String jsonDiff = diffVoltageLevels(network1, network2, voltageLevels, branches, epsilon);
        return jsonDiff;
    }
}
