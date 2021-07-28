/**
 * Copyright (c) 2020-2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.diff.LevelsData;
import com.powsybl.diff.NetworkDiffUtil;
import com.powsybl.iidm.diff.DiffConfig;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Christian Biasuzzi <christian.biasuzzi@techrain.eu>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
class NetworkDiffService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkDiffService.class);

    public static final String DEFAULTLEVELSDATA = "{ \"levels\": [{\"id\": 1, \"i\": 0.1, \"v\": 0.1, \"c\": \"red\" }]}";

    @Autowired
    private NetworkStoreService networkStoreService;

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
        String jsonDiff = new NetworkDiffUtil().diffVoltageLevel(network1, network2, vlId, epsilon, voltageEpsilon);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, vl: {}, threshold: {}, voltageThreshold: {}, diff: {}", network1Uuid, network2Uuid, vlId, epsilon, voltageEpsilon, jsonDiff);
        return jsonDiff;
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId) {
        return getVoltageLevelSvgDiff(network1Uuid, network2Uuid, vlId, DiffConfig.EPSILON_DEFAULT, DiffConfig.EPSILON_DEFAULT, DEFAULTLEVELSDATA);
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon) {
        return getVoltageLevelSvgDiff(network1Uuid, network2Uuid, vlId, epsilon, epsilon, DEFAULTLEVELSDATA);
    }

    public String getVoltageLevelSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon, double voltageEpsilon, String levels) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(vlId);
        Objects.requireNonNull(levels);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);

        LevelsData levelsData = LevelsData.parseData(levels);
        LOGGER.info("levels data: {}", levelsData);

        return new NetworkDiffUtil().getVoltageLevelSvgDiff(network1, network2, vlId, epsilon, voltageEpsilon, levelsData);
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
        Objects.requireNonNull(levels);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);

        LevelsData levelsData = LevelsData.parseData(levels);
        LOGGER.info("levels data: {}", levelsData);

        return new NetworkDiffUtil().getSubstationSvgDiff(network1, network2, substationId, epsilon, voltageEpsilon, levelsData);
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
        String jsonDiff = new NetworkDiffUtil().diffSubstation(network1, network2, substationId, epsilon, voltageEpsilon);
        LOGGER.info("network1 uuid: {}, network2 uuid: {}, substation: {}, threshold: {}, voltageThreshold: {}, diff: {}", network1Uuid, network2Uuid, substationId, epsilon, voltageEpsilon, jsonDiff);
        return jsonDiff;
    }

    public String getVoltageLevelMergedSvgDiff(UUID network1Uuid, UUID network2Uuid, String vlId, double epsilon, double voltageEpsilon, String levels, boolean showCurrent) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(vlId);
        Objects.requireNonNull(levels);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);

        LevelsData levelsData = LevelsData.parseData(levels);
        LOGGER.info("levels data: {}", levelsData);

        return new NetworkDiffUtil().getVoltageLevelMergedSvgDiff(network1, network2, vlId, epsilon, voltageEpsilon, levelsData, showCurrent);
    }

    public String getSubstationMergedSvgDiff(UUID network1Uuid, UUID network2Uuid, String substationId, double epsilon, double voltageEpsilon, String levels, boolean showCurrent) {
        Objects.requireNonNull(network1Uuid);
        Objects.requireNonNull(network2Uuid);
        Objects.requireNonNull(substationId);
        Objects.requireNonNull(levels);
        Network network1 = getNetwork(network1Uuid);
        Network network2 = getNetwork(network2Uuid);

        LevelsData levelsData = LevelsData.parseData(levels);
        LOGGER.info("levels data: {}", levelsData);

        return new NetworkDiffUtil().getSubstationMergedSvgDiff(network1, network2, substationId, epsilon, voltageEpsilon, levelsData, showCurrent);
    }
}
