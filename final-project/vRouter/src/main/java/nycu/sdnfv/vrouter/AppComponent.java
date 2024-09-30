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
package nycu.sdnfv.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TCP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
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
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteTableId;
import org.onosproject.routeservice.RouteInfo;


import java.util.HashSet;
import java.util.Set;
import java.util.Properties;
import java.util.Optional;

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
    
    // ONOS Config Service
    private final vRouterConfigListener vRouterListener = new vRouterConfigListener(); // Instantiate NameConfigListener
    private final ConfigFactory<ApplicationId, vRouterConfig> factory =
        new ConfigFactory<ApplicationId, vRouterConfig>(
        APP_SUBJECT_FACTORY, vRouterConfig.class, "router") { // Instantiate ConfigFactory
            @Override
            public vRouterConfig createConfig() {
                return new vRouterConfig();
            }
    };

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

     @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService intfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    ApplicationId appId;
    ConnectPoint quaggaConnectPoint;
    MacAddress quaggaMacAddress;

    IpAddress vRouterIpAddress;
    MacAddress vRouterMacAddress;

    List<IpAddress> peers = new ArrayList<IpAddress>();
    List<ConnectPoint> peerCPs = new ArrayList<ConnectPoint>();

    enum PacketHandlingState {
        OUTGOING_SDN_TO_EXTERNAL,
        INCOMING_EXTERNAL_TO_SDN,
        INCOMING_EXTERNAL_THROUGH_SDN,
        UNHANDLED
    };

    private vRouterPacketProcessor vRouterProcessor = new vRouterPacketProcessor();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");

        packetService.addProcessor(vRouterProcessor, PacketProcessor.director(6));
        cfgService.addListener(vRouterListener);

        cfgService.registerConfigFactory(factory);

        requestIntercepts();

        log.info("Started the virtual router");
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();

        cfgService.removeListener(vRouterListener);
        cfgService.unregisterConfigFactory(factory);

        packetService.removeProcessor(vRouterProcessor);
        vRouterProcessor = null;

        log.info("Stopped the virtual router");
    }

    // @Modified
    // public void modified(ComponentContext context) {
    //     Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
    //     if (context != null) {
    //         someProperty = get(properties, "someProperty");
    //     }
    //     log.info("Reconfigured");
    // }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    /**
    * Request DHCP packet in via packet service.
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

     private class vRouterConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
              && event.configClass().equals(vRouterConfig.class)) {
                vRouterConfig config = cfgService.getConfig(appId, vRouterConfig.class);

                if (config != null) {
                    quaggaConnectPoint = config.getQuaggaConnectPoint();
                    quaggaMacAddress = config.getQuaggaMacAddress();
                    vRouterIpAddress = config.getVrouterIPAddress();
                    vRouterMacAddress = config.getVrouterMacAddress();
                    peers = config.getPeers();

                    log.info("Quagga is connected to: `{}`, port `{}`",
                            quaggaConnectPoint.deviceId(), quaggaConnectPoint.port());
                    log.info("Quagga-mac is: `{}`",
                            quaggaMacAddress);

                    log.info("Virtual-ip is: `{}`",
                            vRouterIpAddress);
                    log.info("Virtual-mac is: `{}`",
                            vRouterMacAddress);

                    for (IpAddress ip : peers) {
                        // Process each IpAddress
                        log.info("Peer is: `{}`",
                                ip);
                    }
                    for (IpAddress peerIp : peers) {
                        Interface peerIntf = intfService.getMatchingInterface(peerIp);
                        ConnectPoint peerCP = peerIntf.connectPoint();

                        IpAddress bgpSpeakersIp = peerIntf.ipAddressesList().get(0).ipAddress();

                        TrafficSelector peerToSpeakerSelector;
                        TrafficSelector speakerToPeerSelector;
                        peerToSpeakerSelector = vRouerSelectorBase(bgpSpeakersIp.toIpPrefix())
                                                .build();
                        speakerToPeerSelector = vRouerSelectorBase(peerIp.toIpPrefix())
                                                .build();

                        // BGP traffic <->
                        // Flow rules for BGP Peering - Incoming eBGP
                        ebgpIntent(peerCP, quaggaConnectPoint, peerToSpeakerSelector);
                        // Flow rules for BGP Peering - Outgoing eBGP
                        ebgpIntent(quaggaConnectPoint, peerCP, speakerToPeerSelector);

                        // Save the connection points
                        peerCPs.add(peerCP);
                    }
                }
            }
        }

        // Create Intent for each Peers from vRouter in SDN network
        public void ebgpIntent(ConnectPoint ingress, ConnectPoint egress, TrafficSelector selector) {
            TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

            log.info("eBGP Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
            ingress.deviceId(), ingress.port(), egress.deviceId(), egress.port());

            PointToPointIntent intent = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector)
                .treatment(treatment)
                .filteredIngressPoint(ingressPoint)
                .filteredEgressPoint(egressPoint)
                .priority(30)
                .build();

            intentService.submit(intent);
        }
    }


    private class vRouterPacketProcessor implements PacketProcessor {
        boolean isPktHandled;

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }

            InboundPacket pktIn = context.inPacket();

            Ethernet ethPktIn = pktIn.parsed();

            if (ethPktIn == null) {
                return;
            }

            // Prevent ARP packets into this process
            if (ethPktIn.getEtherType() == Ethernet.TYPE_ARP) {
                return;
            }

            // Process BGP packets
            IPv4 ipv4PktIn = (IPv4) ethPktIn.getPayload();
            if (ipv4PktIn.getProtocol() == IPv4.PROTOCOL_TCP) {
                TCP tcpPkt = (TCP) ipv4PktIn.getPayload();
                if (tcpPkt.getDestinationPort() == 179) {
                    log.info("Get BGP Packet, which should be already handled");
                    return;
                }
            }
            log.info("*****(Virtual Router Packet Processing)*****");
            isPktHandled = true;

            IpAddress ipDst = IpAddress.valueOf(ipv4PktIn.getDestinationAddress());
            MacAddress macDst = ethPktIn.getDestinationMAC();
            log.info("IP of Destination: " + ipDst);
            log.info("Mac of Destination: " + macDst);

            ConnectPoint ingress = pktIn.receivedFrom();

            Host hostDst = null;
            Set<Host> hostDsts = hostService.getHostsByIp(ipDst);
            if (hostDsts.size() > 0) {
                hostDst = new ArrayList<Host>(hostDsts).get(0);
            }

            // ResolvedRoute nextHopRoute = null;
            // Collection<ResolvedRoute> nextHopRoutes = routeService.getAllResolvedRoutes(ipDst.toIpPrefix());   
            // if (!nextHopRoutes.isEmpty()) {
            //     nextHopRoute = nextHopRoutes.iterator().next();
            // }
            ResolvedRoute nextHopRoute = getRoute(ipDst);

            PacketHandlingState state = determineState(macDst, ipDst, hostDst, nextHopRoute);
            switch (state) {
            case OUTGOING_SDN_TO_EXTERNAL:
                handleOutgoingTraffic(ingress, ipDst, nextHopRoute, true);
                break;

            case INCOMING_EXTERNAL_TO_SDN:
                handleIncomingTraffic(hostDst, ingress, ipDst);
                break;

            case INCOMING_EXTERNAL_THROUGH_SDN:
                handleOutgoingTraffic(ingress, ipDst, nextHopRoute, false);
                break;

            case UNHANDLED:
                isPktHandled = false;
                break;
            default:

                break;
            }

            if (isPktHandled) {
                context.block();
            }
        }

        private ResolvedRoute getRoute(IpAddress ipDst) {
            return routeService.getRouteTables().stream() // Stream of RouteTableId
                    .flatMap(tableID -> routeService.getRoutes(tableID).stream()) // Stream of RouteInfo
                    .map(RouteInfo::bestRoute)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(route -> route.prefix().contains(ipDst))
                    .findFirst()
                    .orElse(null);
        }

        private void sdnToExternalIntent(ConnectPoint ingress, ConnectPoint egress,
                        TrafficSelector selector, TrafficTreatment treatment) {

            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);


            log.info("SDN-External Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
            ingress.deviceId(), ingress.port(), egress.deviceId(), egress.port());

            PointToPointIntent intent = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector)
                .treatment(treatment)
                .filteredIngressPoint(ingressPoint)
                .filteredEgressPoint(egressPoint)
                .priority(40)
                .build();
            intentService.submit(intent);
        }

        // External to External traffic
        private void transitIntent(ConnectPoint egress,
                        TrafficSelector selector, TrafficTreatment treatment) {

            Set<FilteredConnectPoint> ingressPoints = new HashSet<FilteredConnectPoint>();
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

            for (ConnectPoint cp : peerCPs) {
                if (!cp.equals(egress)) {
                    ingressPoints.add(new FilteredConnectPoint(cp));
                    log.info("Transit Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    cp.deviceId(), cp.port(), egress.deviceId(), egress.port());
                }
            }

            MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                .appId(appId)
                .selector(selector)
                .treatment(treatment)
                .filteredIngressPoints(ingressPoints)
                .filteredEgressPoint(egressPoint)
                .priority(50)
                .build();
            intentService.submit(intent);
        }

        private void handleOutgoingTraffic(ConnectPoint ingress, IpAddress ipDst, ResolvedRoute isRouted, boolean fromInternal) {

            IpAddress nextHopIp = isRouted.nextHop();
            MacAddress nextHopMac = isRouted.nextHopMac();

            ConnectPoint egress = intfService.getMatchingInterface(nextHopIp).connectPoint();

            TrafficTreatment outgoingTreatment;
            outgoingTreatment = vRouerTreatmentBase(nextHopMac, quaggaMacAddress)
                                    .build();

            TrafficSelector outgoingSelector;
            if (fromInternal) {
                outgoingSelector = vRouerSelectorBase(ipDst.toIpPrefix())
                                        .build();
                sdnToExternalIntent(ingress, egress, outgoingSelector, outgoingTreatment);
            } else {
                ResolvedRoute routeDst = routeService.longestPrefixLookup(ipDst).get();
                outgoingSelector = vRouerSelectorBase(routeDst.prefix())
                                        .build();
                transitIntent(egress, outgoingSelector, outgoingTreatment);
            }


        }

        private void handleIncomingTraffic(Host hostDst, ConnectPoint ingress, IpAddress ipDst) {
            ConnectPoint egress = ConnectPoint.fromString(hostDst.location().toString());
            MacAddress macHost = hostDst.mac();

            TrafficSelector incomingSelector;
            TrafficTreatment incomingTreatment;
            incomingSelector = vRouerSelectorBase(ipDst.toIpPrefix())
                                    .build();
            incomingTreatment = vRouerTreatmentBase(macHost, vRouterMacAddress)
                                    .build();
            sdnToExternalIntent(ingress, egress, incomingSelector, incomingTreatment);
        }


        private PacketHandlingState determineState(MacAddress macDst, IpAddress ipDst, Host hostDst, ResolvedRoute isRouted) {
            if (macDst.equals(vRouterMacAddress) && isRouted != null) {
                return PacketHandlingState.OUTGOING_SDN_TO_EXTERNAL;
            } else if (macDst.equals(quaggaMacAddress)) {
                if (hostDst != null) {
                    return PacketHandlingState.INCOMING_EXTERNAL_TO_SDN;
                } else if (isRouted != null) {
                    return PacketHandlingState.INCOMING_EXTERNAL_THROUGH_SDN;
                }
            }
            return PacketHandlingState.UNHANDLED;
        }
    }
  
    private TrafficSelector.Builder vRouerSelectorBase(IpPrefix ipPrefix) {
        return DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(ipPrefix);
    }

    private TrafficTreatment.Builder vRouerTreatmentBase(MacAddress macDst, MacAddress macSrc) {
        return DefaultTrafficTreatment.builder()
                .setEthDst(macDst)
                .setEthSrc(macSrc);
    }
    
}