/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.diff.server;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Christian Biasuzzi <christian.biasuzzi@techrain.eu>
 */
@RestController
@RequestMapping(value = "/" + NetworkDiffApi.API_VERSION + "/")
@Api(tags = "network-diff-server")
@ComponentScan(basePackageClasses = NetworkDiffService.class)
public class NetworkDiffController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkDiffController.class);

    @Inject
    private NetworkDiffService networkDiffService;

    @GetMapping(value = "/networks/{network1Uuid}/diff/{network2Uuid}/vl/{vlId}")
    @ApiOperation(value = "compare two networks voltage levels", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "network diff")})
    public ResponseEntity<String> diffNetworks(
            @ApiParam(value = "Network1 UUID") @PathVariable("network1Uuid") UUID network1Uuid,
            @ApiParam(value = "Network2 UUID") @PathVariable("network2Uuid") UUID network2Uuid,
            @ApiParam(value = "Voltage level ID") @PathVariable("vlId") String vlId) {

        String jsonDiff = networkDiffService.diff(network1Uuid, network2Uuid, vlId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(jsonDiff);
    }

    @GetMapping(value = "/networks")
    @ApiOperation(value = "get network ids", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "network iids")})
    public ResponseEntity<Map<UUID, String>> getNetworkIds() {

        Map<UUID, String> netIds = networkDiffService.getNetworkIds();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(netIds);
    }

    @GetMapping(value = "/svg/network/{network1Uuid}/vl/{vlId}/{diffs}")
    @ApiOperation(value = "get voltage level svg diagram", produces = "image/svg+xml")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "network diff")})
    public ResponseEntity<String> getSvg(
            @ApiParam(value = "Network1 UUID") @PathVariable("network1Uuid") UUID network1Uuid,
            @ApiParam(value = "Voltage level ID") @PathVariable("vlId") String vlId,
            @ApiParam(value = "Swithes diffs") @PathVariable("diffs") String diffs) {
        String svg = networkDiffService.getVoltageLevelSvg(network1Uuid, vlId, Arrays.asList(diffs.split(",")));
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    @GetMapping(value = "/networks/{network1Uuid}/svgdiff/{network2Uuid}/vl/{vlId}")
    @ApiOperation(value = "get voltage level svg diff diagram", produces = "image/svg+xml")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "network diff")})
    public ResponseEntity<String> getSvg(
            @ApiParam(value = "Network1 UUID") @PathVariable("network1Uuid") UUID network1Uuid,
            @ApiParam(value = "Network2 UUID") @PathVariable("network2Uuid") UUID network2Uuid,
            @ApiParam(value = "Voltage level ID") @PathVariable("vlId") String vlId) {
        String svg = networkDiffService.getVoltageLevelSvgDiff(network1Uuid, network2Uuid, vlId);
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    @GetMapping(value = "/networks/{network1Uuid}/svgdiff/{network2Uuid}/sub/{subId}")
    @ApiOperation(value = "get substation svg diff diagram", produces = "image/svg+xml")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "network diff")})
    public ResponseEntity<String> getSubSvg(
            @ApiParam(value = "Network1 UUID") @PathVariable("network1Uuid") UUID network1Uuid,
            @ApiParam(value = "Network2 UUID") @PathVariable("network2Uuid") UUID network2Uuid,
            @ApiParam(value = "Substation ID") @PathVariable("subId") String subId) {
        String svg = networkDiffService.getSubstationSvgDiff(network1Uuid, network2Uuid, subId);
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    @GetMapping(value = "/networks/{network1Uuid}/diff/{network2Uuid}/sub/{subId}")
    @ApiOperation(value = "compare two networks substations", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "network diff")})
    public ResponseEntity<String> diffSubstation(
            @ApiParam(value = "Network1 UUID") @PathVariable("network1Uuid") UUID network1Uuid,
            @ApiParam(value = "Network2 UUID") @PathVariable("network2Uuid") UUID network2Uuid,
            @ApiParam(value = "Substation ID") @PathVariable("subId") String subId) {

        String jsonDiff = networkDiffService.diffSubstation(network1Uuid, network2Uuid, subId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(jsonDiff);
    }
}
