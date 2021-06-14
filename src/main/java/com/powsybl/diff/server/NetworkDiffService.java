/**
 * Copyright (c) 2020-2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.diff.*;
import com.powsybl.iidm.network.*;
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
            LOGGER.error(e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    Map<UUID, String> getNetworkIds() {
        return networkStoreService.getNetworkIds();
    }

    //voltage levels
    public String diffVoltageLevel(UUID network1Uuid, UUID network2Uuid, String vlId) {
        return diffVoltageLevel(network1Uuid, network2Uuid, vlId, DiffConfig.EPSILON_DEFAULT, DiffConfig.EPSILON_DEFAULT);
    }

    public String diffVoltageLevel(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon) {
        return diffVoltageLevel(network1Uuid, network2Uuid, vlId, epsilon, epsilon);
    }

    public String diffVoltageLevel(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon, double voltageEpsilon) {
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
        String jsonDiff = diffVoltageLevel(network1, network2, vlId, epsilon, voltageEpsilon);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, vl: {}, threshold: {}, voltageThreshold: {}, diff: {}", network1Uuid, network2Uuid, vlId, epsilon, voltageEpsilon, jsonDiff);
        return jsonDiff;
    }

    private String diffVoltageLevel(Network network1, Network network2, String vlId, double epsilon, double voltageEpsilon) {
        List<String> voltageLevels = Collections.singletonList(vlId);
        List<String> branches = network1.getVoltageLevel(vlId).getConnectableStream(Branch.class).map(Branch::getId).collect(Collectors.toList());
        return diffNetworks(network1, network2, voltageLevels, branches, epsilon, voltageEpsilon);
    }

    private String diffNetworks(Network network1, Network network2, List<String> voltageLevels, List<String> branches, double epsilon, double voltageEpsilon) {
        DiffEquipment diffEquipment = new DiffEquipment();
        diffEquipment.setVoltageLevels(voltageLevels);
        List<DiffEquipmentType> equipmentTypes = new ArrayList<DiffEquipmentType>();
        equipmentTypes.add(DiffEquipmentType.VOLTAGE_LEVELS);
        if (!branches.isEmpty()) {
            equipmentTypes.add(DiffEquipmentType.BRANCHES);
            diffEquipment.setBranches(branches);
        }
        diffEquipment.setEquipmentTypes(equipmentTypes);
        NetworkDiff ndiff = new NetworkDiff(new DiffConfig(epsilon, voltageEpsilon, DiffConfig.FILTER_DIFF_DEFAULT));
        NetworkDiffResults diffVl = ndiff.diff(network1, network2, diffEquipment);
        String jsonDiff = NetworkDiff.writeJson(diffVl);
        //NaN is not part of the JSON standard and frontend would fail when parsing it
        //it should be handled at the source, though
        jsonDiff = jsonDiff.replace(": NaN,", ": \"Nan\",");
        jsonDiff = jsonDiff.replace(": Infinity,", ": \"Infinity\",");
        jsonDiff = jsonDiff.replace(": -Infinity,", ": \"-Infinity\",");
        return jsonDiff;
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId) {
        return getVoltageLevelSvgDiff(network1Uuid, network2Uuid, vlId, DiffConfig.EPSILON_DEFAULT, DiffConfig.EPSILON_DEFAULT, null);
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon) {
        return getVoltageLevelSvgDiff(network1Uuid, network2Uuid, vlId, epsilon, epsilon, null);
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon, double voltageEpsilon, String levels) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(vlId);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);

        LevelsData levelsData = parseLevelsData(levels);
        LOGGER.info("levels data: {}", levelsData);

        return getVoltageLevelSvgDiff(network1, network2, vlId, epsilon, voltageEpsilon, levelsData);
    }

    private LevelsData parseLevelsData(String levels) {
        if (levels != null) {
            LevelsData levelsData = LevelsData.parseData(levels, true);
            return levelsData;
        } else {
            return null;
        }
    }

    private String getVoltageLevelSvgDiff(Network network1, Network network2, String vlId, double epsilon, double voltageEpsilon, LevelsData levelsData) {
        try {
            String jsonDiff = diffVoltageLevel(network1, network2, vlId, epsilon, voltageEpsilon);
//            DiffData diffData = new DiffData(jsonDiff);
//            return writeVoltageLevelSvg(network1, vlId, new DiffStyleProvider(diffData));
            ColorsLevelsDiffData diffData = new ColorsLevelsDiffData(jsonDiff);
//            return writeVoltageLevelSvg(network1, vlId, new ColorsLevelsDiffStyleProvider(diffData, new ColorsLevelsDiffConfig(0, 10, true)));
            return writeVoltageLevelSvg(network1, vlId, new MultipleColorsLevelsDiffStyleProvider(diffData, levelsData, true));
        } catch (PowsyblException | IOException e) {
            LOGGER.error(e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String writeVoltageLevelSvg(Network network, String vlId, ExtendedDiagramStyleProvider styleProvider) {
        String svgData;
        String metadataData;
        String jsonData;
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter();
             StringWriter jsonWriter = new StringWriter()) {
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
        return getSubstationSvgDiff(network1Uuid, network2Uuid, substationId, DiffConfig.EPSILON_DEFAULT, DiffConfig.EPSILON_DEFAULT, null);
    }

    public String getSubstationSvgDiff(UUID network1Uuid, UUID network2Uuid, String substationId, double epsilon) {
        return getSubstationSvgDiff(network1Uuid, network2Uuid, substationId, epsilon, epsilon, null);
    }

    public String getSubstationSvgDiff(UUID network1Uuid, UUID network2Uuid, String substationId, double epsilon, double voltageEpsilon, String levels) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(substationId);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);

        LevelsData levelsData = parseLevelsData(levels);
        LOGGER.info("levels data: {}", levelsData);

        return getSubstationSvgDiff(network1, network2, substationId, epsilon, voltageEpsilon, levelsData);
    }

    private String getSubstationSvgDiff(Network network1, Network network2, String substationId, double epsilon, double voltageEpsilon, LevelsData levelsData) {
        try {
            String jsonDiff = diffSubstation(network1, network2, substationId, epsilon, voltageEpsilon);
//            DiffData diffData = new DiffData(jsonDiff);
//            return writeSubstationSvg(network1, substationId, new DiffStyleProvider(diffData));
            ColorsLevelsDiffData diffData = new ColorsLevelsDiffData(jsonDiff);
//            return writeSubstationSvg(network1, substationId, new ColorsLevelsDiffStyleProvider(diffData, new ColorsLevelsDiffConfig(0, 10, true)));
            return writeSubstationSvg(network1, substationId, new MultipleColorsLevelsDiffStyleProvider(diffData, levelsData, true));
        } catch (PowsyblException | IOException e) {
            LOGGER.error(e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String writeSubstationSvg(Network network, String substationId, ExtendedDiagramStyleProvider styleProvider) {
        String svgData;
        String metadataData;
        String jsonData;
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter();
             StringWriter jsonWriter = new StringWriter()) {
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
        return diffSubstation(network1Uuid, network2Uuid, substationId, DiffConfig.EPSILON_DEFAULT, DiffConfig.EPSILON_DEFAULT);
    }

    public String diffSubstation(UUID network1Uuid, UUID network2Uuid, String substationId, double epsilon) {
        return diffSubstation(network1Uuid, network2Uuid, substationId, epsilon, epsilon);
    }

    public String diffSubstation(UUID network1Uuid, UUID network2Uuid, String substationId, double epsilon, double voltageEpsilon) {
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
        String jsonDiff = diffSubstation(network1, network2, substationId, epsilon, voltageEpsilon);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, substation: {}, threshold: {}, voltageThreshold: {}, diff: {}", network1Uuid, network2Uuid, substationId, epsilon, voltageEpsilon, jsonDiff);
        return jsonDiff;
    }

    private String diffSubstation(Network network1, Network network2, String substationId, double epsilon, double voltageEpsilon) {
        Substation substation1 = network1.getSubstation(substationId);
        List<String> voltageLevels = substation1.getVoltageLevelStream().map(VoltageLevel::getId)
                .collect(Collectors.toList());
        List<String> branches = substation1.getVoltageLevelStream().flatMap(vl -> vl.getConnectableStream(Line.class))
                .map(Line::getId).collect(Collectors.toList());
        List<String> twts = substation1.getTwoWindingsTransformerStream().map(TwoWindingsTransformer::getId)
                .collect(Collectors.toList());
        branches.addAll(twts);
        String jsonDiff = diffNetworks(network1, network2, voltageLevels, branches, epsilon, voltageEpsilon);
        return jsonDiff;
    }
}
