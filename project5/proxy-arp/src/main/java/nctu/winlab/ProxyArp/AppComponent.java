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
package nctu.winlab.ProxyArp;

// import org.jboss.netty.handler.ipfilter.IpV4Subnet;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import java.util.Optional;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.onlab.util.Tools.get;

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
    protected EdgePortService edgePortService;

    private ProxyArpProcessor proxyArp = new ProxyArpProcessor();


    private ApplicationId appId;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());

        appId = coreService.registerApplication("nctu.winlab.ProxyArp");

        packetService.addProcessor(proxyArp, PacketProcessor.director(2));

        requestIntercepts();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);

        withdrawIntercepts();

        packetService.removeProcessor(proxyArp);
        proxyArp = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
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
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

    }


    private class ProxyArpProcessor implements PacketProcessor {

        private final ArpTable arpTable;

        private Ethernet ethPktIn;

        public ProxyArpProcessor() {
            arpTable = new ArpTable();
        }

        @Override
        public void process(PacketContext context) {
            // log.info("Proxy ARP processing packets...");

            // Stop processing if the packet has been handled, since we
            // can't do any more to it.

            if (context.isHandled()) {
                return;
            }

            InboundPacket pktIn = context.inPacket();

            ethPktIn = pktIn.parsed();

            if (ethPktIn == null) {
                return;
            }

            // get source mac address
            MacAddress macSrc = ethPktIn.getSourceMAC();

            // get ARP payload
            ARP arpPktIn = (ARP) ethPktIn.getPayload();
            Ip4Address ipSrc = Ip4Address.valueOf(arpPktIn.getSenderProtocolAddress());
            Ip4Address ipDst = Ip4Address.valueOf(arpPktIn.getTargetProtocolAddress());

            // get src edge
            ConnectPoint cpSrc = pktIn.receivedFrom(); // deviceId, port

            // update the arp table
            arpTable.setEntry(ipSrc, macSrc);
            arpTable.setEdge(ipSrc, cpSrc);

            // either ARP request or reply
            switch (arpPktIn.getOpCode()) {
            case ARP.OP_REQUEST:
                MacAddress macDst = arpTable.getEntry(ipDst);

                // handle arpRequest
                arpRequestHandler(ipDst, macDst, cpSrc);
                break;
            case ARP.OP_REPLY:

                // handle arpResponse
                arpResponseHandler(ipDst, macSrc);
                break;
            default:
                break;
            }

        }

        private void arpRequestHandler(Ip4Address ipDst, MacAddress macDst, ConnectPoint cpSrc) {
            if (macDst == null) { // arp table miss, floods ARP Request to edge ports except the port receiving
                log.info(
                    String.format("TABLE MISS. Send request to edge ports"
                    )
                );

                // flood ARP request except the sender
                for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                    if (!cp.equals(cpSrc)) {

                        emitPacket(cp, ethPktIn);

                        // TODO: edgePortService would have mutiple reply
                        // TrafficTreatment treatment = arpTreatment(connectPoint);
                        // Optional<TrafficTreatment> optionalTreatment = Optional.of(treatment);
                        // ByteBuffer ethPktOut = ByteBuffer.wrap(ethPktIn.serialize());
                        // OutboundPacket pktOut = new DefaultOutboundPacket(
                        //     connectPoint.deviceId(), treatment, ethPktOut);
                        // edgePortService.emitPacket(connectPoint.deviceId(), ethPktOut, optionalTreatment);
                    }
                }
            } else { // arp table hit, packet out arp reply target MAC to the sender
                log.info(
                    String.format("TABLE HIT. Requested MAC = %s",
                    macDst)
                );

                Ethernet arpReply = ARP.buildArpReply(ipDst, macDst, ethPktIn);
                emitPacket(cpSrc, arpReply);

                // TODO: edgePortService would have mutiple reply
                // TrafficTreatment treatment = arpTreatment(cpSrc);
                // Optional<TrafficTreatment> optionalTreatment = Optional.of(treatment);
                // ByteBuffer ethPktOut = ByteBuffer.wrap(arpReply.serialize());
                // OutboundPacket pktOut = new DefaultOutboundPacket(cpSrc.deviceId(), treatment, ethPktOut);
                // edgePortService.emitPacket(cpSrc.deviceId(), ethPktOut, optionalTreatment);
            }

        }

        private void arpResponseHandler(Ip4Address ipDst, MacAddress macSrc) {
            log.info(
                String.format("RECV REPLY. Requested MAC = %s",
                macSrc.toString())
            );

            // get dst edge
            // From Spec, Proxy ARP just learns IP-MAC
            // TODO: reply to the source by received reply
            // ConnectPoint cpDst = arpTable.getEdge(ipDst);

            // emitPacket(cpDst, ethPktIn);
        }

        private void emitPacket(ConnectPoint cp, Ethernet ethPkt) {
            TrafficTreatment treatment = arpTreatment(cp);

            ByteBuffer ethPktOut = ByteBuffer.wrap(ethPkt.serialize());

            OutboundPacket pktOut = new DefaultOutboundPacket(cp.deviceId(), treatment, ethPktOut);
            packetService.emit(pktOut);

        }

        private TrafficTreatment arpTreatment(ConnectPoint cp) {
            return DefaultTrafficTreatment.builder()
                    .setOutput(cp.port())
                    .build();
        }
    }

    private class ArpTable {
        private final Map<Ip4Address, MacAddress> hosts;
        private final Map<Ip4Address, ConnectPoint> edges;

        public ArpTable() {
            hosts = new HashMap<>();
            edges = new HashMap<>();
        }

        public void setEntry(Ip4Address ip, MacAddress mac) {
            hosts.put(ip, mac);

        }

        public MacAddress getEntry(Ip4Address ip) {
            return hosts.get(ip);
        }

        public void setEdge(Ip4Address ip, ConnectPoint cp) {
            edges.put(ip, cp);
        }

        public ConnectPoint getEdge(Ip4Address ip) {
            return edges.get(ip);
        }


    }
}
