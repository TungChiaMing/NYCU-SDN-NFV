/*
 * Copyright 2023-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.bridge;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Packet processor required libraries
 */


import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketContext;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;

import org.onosproject.net.flow.FlowRuleService;
// import org.onosproject.net.flow.FlowRule;
// import org.onosproject.net.flow.DefaultFlowRule;

import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;

import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficSelector;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;

/**
 * Java interface
 */
import java.util.Map;
/**
 * Java class
 */

import java.util.HashMap;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })


public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

/** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;


    private LearningBridgeProcessor learningBridge = new LearningBridgeProcessor();


    private ApplicationId appId;

    /** Configure Flow Timeout for installed flow rules; default is 30 sec. */
    private int flowTimeout = 30;

    /** Configure Flow Priority for installed flow rules; default is 30. */
    private int flowPriority = 30;

/** Some configurable property: Packet processor property. */


    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass()); // external configuration inputs

        appId = coreService.registerApplication("nctu.winlab.bridge");

        packetService.addProcessor(learningBridge, PacketProcessor.director(2));
        // topologyService.addListener(topologyListener); // topology forward package
        // readComponentConfiguration(context); // dynamic config app
        requestIntercepts();
        log.info("Let's Get Started", appId.id());
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);

        withdrawIntercepts();
        packetService.removeProcessor(learningBridge);
        learningBridge = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        // Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        // if (context != null) {
        //     someProperty = get(properties, "someProperty");
        // }
        // readComponentConfiguration(context);
        requestIntercepts();

        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

    }



    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    private class LearningBridgeProcessor implements PacketProcessor {

        private final ForwardingTable forwardingTable;

        public LearningBridgeProcessor() {
            this.forwardingTable = new ForwardingTable();
        }


        @Override
        public void process(PacketContext context) {
            // log.info("Learning Bridge processing packets...");

            // Stop processing if the packet has been handled, since we
            // can't do any more to it.

            if (context.isHandled()) {
                return;
            }

            InboundPacket pktIn = context.inPacket();

            Ethernet ethPktIn = pktIn.parsed();

            if (ethPktIn == null) {
                return;
            }


            DeviceId deviceSrcId = pktIn.receivedFrom().deviceId();
            PortNumber portSrc = pktIn.receivedFrom().port();

            MacAddress macSrc = ethPktIn.getSourceMAC();
            MacAddress macDst = ethPktIn.getDestinationMAC();

            // log.info("mac_src: " + macSrc);
            // log.info("mac_dst: " + macDst);


            // update forwarding table
            forwardingTable.setEntry(deviceSrcId, macSrc, portSrc);

            // looks up MAC address
            Map<MacAddress, PortNumber> deviceEntries = forwardingTable.getEntry(deviceSrcId);

            PortNumber portDst = deviceEntries.get(macDst);

            if (portDst == null) {
                // dst mac not found
                log.info(
                    String.format("MAC address `%s` is missed on `%s`. Flood the packet.",
                    macDst.toString(), deviceSrcId.toString())
                );
                packetOut(context, PortNumber.FLOOD);
            } else {
                log.info(
                    String.format("MAC address `%s` is matched on `%s`. Install a flow rule.",
                    macDst.toString(), deviceSrcId.toString())
                );
                packetOut(context, portDst);

                installRule(context, portDst);
            }

        }
    }


    private class ForwardingTable {
        private final Map<DeviceId, Map<MacAddress, PortNumber>> devices;

        public ForwardingTable() {
            this.devices = new HashMap<>();
        }

        public void setEntry(DeviceId deviceId, MacAddress macSrc, PortNumber port) {
            Map<MacAddress, PortNumber> entries = devices.computeIfAbsent(deviceId, k -> new HashMap<>());

            if (!entries.containsKey(macSrc)) {
                log.info(
                    String.format("Add an entry to the port table of `%s`. MAC address: `%s` => Port: `%s`.",
                    deviceId.toString(), macSrc.toString(), port.toString())
                );

                entries.put(macSrc, port);
            }

        }

        public Map<MacAddress, PortNumber> getEntry(DeviceId deviceId) {
            return devices.get(deviceId);
        }


    }

    private void packetOut(PacketContext context, PortNumber port) {
        context.treatmentBuilder().setOutput(port);
        context.send();

        // if (port == PortNumber.FLOOD) {
        //     log.info("Flood packet...");
        // } else {
        //     log.info("Send packet to port " + port);
        // }


    }

    private void installRule(PacketContext context, PortNumber portDst) {

        InboundPacket pktIn = context.inPacket();

        Ethernet ethPktIn = pktIn.parsed();

        DeviceId deviceId = pktIn.receivedFrom().deviceId();

        TrafficSelector selector; // ETH_SRC, ETH_DST
        selector =  DefaultTrafficSelector.builder()
                    .matchEthSrc(ethPktIn.getSourceMAC())
                    .matchEthDst(ethPktIn.getDestinationMAC())
                    .build();

        TrafficTreatment treatment; // Port
        treatment = DefaultTrafficTreatment.builder()
                    .setOutput(portDst)
                    .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                                                .withSelector(selector)
                                                .withTreatment(treatment)
                                                .withPriority(flowPriority)
                                                .makeTemporary(flowTimeout)
                                                .withFlag(ForwardingObjective.Flag.VERSATILE) // must set
                                                .fromApp(appId) // must set
                                                .add();
        flowObjectiveService.forward(deviceId, forwardingObjective);

    }


}
