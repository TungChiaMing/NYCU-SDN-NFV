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
package nctu.winlab.unicastdhcp;

// import org.onosproject.cfg.ComponentConfigService;
// import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
// import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import com.esotericsoftware.reflectasm.PublicConstructorAccess;

// import java.io.ObjectInputFilter.Config;
// import java.util.Dictionary;
// import java.util.Properties;

// import static org.onlab.util.Tools.get;

/**
 * ONOS Config Packages
 */

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// other packages
// import org.onlab.packet.DHCP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
// import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
// import org.onosproject.net.intent.IntentState;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    // ONOS Config Service
    private final DhcpConfigListener cfgListener = new DhcpConfigListener(); // Instantiate NameConfigListener
    private final ConfigFactory<ApplicationId, DhcpConfig> factory =
      new ConfigFactory<ApplicationId, DhcpConfig>(
        APP_SUBJECT_FACTORY, DhcpConfig.class, "UnicastDhcpConfig") { // Instantiate ConfigFactory
        @Override
        public DhcpConfig createConfig() {
            return new DhcpConfig();
        }
    };

    /** Some variables. */
    private ApplicationId appId;

    private UnicastDhcpProcessor unicastdhcpProcessor = new UnicastDhcpProcessor();

    /** Some configurable property. */
    // private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService; // ComponentConfigService

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;


     // packetService and intentService
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;


    @Activate
    protected void activate() {
        // cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener); // Register ConfigListener
        cfgService.registerConfigFactory(factory); // Register ConfigFactory

        packetService.addProcessor(unicastdhcpProcessor, PacketProcessor.director(1));

        requestIntercepts();

        log.info("[INFO] Started unicastdhcp app");
    }

    @Deactivate
    protected void deactivate() {
        // cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();
        purgeIntents();

        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);

        packetService.removeProcessor(unicastdhcpProcessor);
        unicastdhcpProcessor = null;

        log.info("[INFO] Stopped unicastdhcp app");
    }

    /**
     * Request DHCP packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector selector;
        selector = dhcpSelectorBase(UDP.DHCP_CLIENT_PORT, UDP.DHCP_SERVER_PORT)
                    .build();

        // request all traffic matching the selector
        packetService.requestPackets(selector, PacketPriority.CONTROL, appId);

    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector selector;
        selector = dhcpSelectorBase(UDP.DHCP_CLIENT_PORT, UDP.DHCP_SERVER_PORT)
                    .build();

        // cancel all traffic matching the selector
        packetService.cancelPackets(selector, PacketPriority.CONTROL, appId);

    }

    private void purgeIntents() {
        Iterable<Intent> intentsIterable = intentService.getIntentsByAppId(appId);

        List<Key> keyList = new ArrayList<>();

        for (Intent intent : intentsIterable) {
            intentService.withdraw(intent);
            keyList.add(intent.key());
        }

        // TODO: Purge the intent
        // the following source code don't really purge it

        // for (Key key : keyList) {

        //     IntentState state = intentService.getIntentState(key);

        //     while (true) {
        //         if (state == IntentState.WITHDRAWN) {
        //             log.info("[INFO] Purge the intent...");
        //             intentService.purge(intentService.getIntent(key));
        //             break;
        //         }
        //     }
        // }

    }


    private class DhcpConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
          if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
              && event.configClass().equals(DhcpConfig.class)) {
            DhcpConfig config = cfgService.getConfig(appId, DhcpConfig.class);

            ConnectPoint connectPoint = config.getConnectPoint();
            if (config != null) {

              log.info("DHCP server is connected to `{}`, port `{}`", connectPoint.deviceId(), connectPoint.port());
            }
          }
        }
    }

    private class UnicastDhcpProcessor implements PacketProcessor {

        private Map<ConnectPoint, ConnectPoint> connectPointMap = new HashMap<>();

        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }

            // log.info("[INFO] UnicastDhcp App Processing the packets...");

            InboundPacket pktIn = context.inPacket();

            Ethernet ethPktIn = pktIn.parsed();

            if (ethPktIn == null) {
                return;
            }

            // Prevent ARP packets into this process
            if (ethPktIn.getEtherType() == Ethernet.TYPE_ARP) {
                return;
            }

            // Connection Point.
            // DeviceId deviceSrcId = pktIn.receivedFrom().deviceId();
            // PortNumber portSrc = pktIn.receivedFrom().port();

            // source
            ConnectPoint srcConnectionPoint = pktIn.receivedFrom();

            // destincation
            ConnectPoint dhcpServerConnectionPoint = cfgService.getConfig(appId, DhcpConfig.class).getConnectPoint();

            // MacAddress clientMac = ethPktIn.getSourceMAC();
            // MacAddress macDst = ethPktIn.getDestinationMAC();

            if (srcConnectionPoint == dhcpServerConnectionPoint) {
                return;
            }

            if (connectPointMap.containsKey(srcConnectionPoint) &&
               connectPointMap.get(srcConnectionPoint).equals(dhcpServerConnectionPoint)) {
                return;
            }
            connectPointMap.put(srcConnectionPoint, dhcpServerConnectionPoint);

            // To cover both directions (from client to server and vice-versa)
            // log.info("[INFO] Intent from client to server and vice-versa");

            TrafficSelector clientToServerSelector;
            TrafficSelector serverToClientSelector;

            // clientToServerSelector = dhcpSelectorBase(UDP.DHCP_CLIENT_PORT, UDP.DHCP_SERVER_PORT)
            //                          .matchEthSrc(clientMac)
            //                          .build();


            // serverToClientSelector = dhcpSelectorBase(UDP.DHCP_SERVER_PORT, UDP.DHCP_CLIENT_PORT)
            //                          .matchEthDst(clientMac)
            //                          .build();

            clientToServerSelector = dhcpSelectorBase(UDP.DHCP_CLIENT_PORT, UDP.DHCP_SERVER_PORT)
                                     .build();


            serverToClientSelector = dhcpSelectorBase(UDP.DHCP_SERVER_PORT, UDP.DHCP_CLIENT_PORT)
                                     .build();

            createIntent(srcConnectionPoint, dhcpServerConnectionPoint, clientToServerSelector);
            createIntent(dhcpServerConnectionPoint, srcConnectionPoint, serverToClientSelector);

        }
        private void createIntent(ConnectPoint ingress,
                                ConnectPoint egress, TrafficSelector selector) {

            TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                ingress.deviceId(), ingress.port(), egress.deviceId(), egress.port());

            PointToPointIntent intent = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector)
                .treatment(treatment)
                .filteredIngressPoint(ingressPoint)
                .filteredEgressPoint(egressPoint)
                .priority(45000)
                .build();

            intentService.submit(intent);
        }



    }

    private TrafficSelector.Builder dhcpSelectorBase(int udpSrc, int udpDst) {
        return DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpSrc(TpPort.tpPort(udpSrc))
                .matchUdpDst(TpPort.tpPort(udpDst));
    }

}
