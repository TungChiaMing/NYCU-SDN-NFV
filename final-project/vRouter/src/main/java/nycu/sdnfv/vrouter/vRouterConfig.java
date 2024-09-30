package nycu.sdnfv.vrouter;


import java.util.List;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.Config; // config notification package
import java.util.stream.Collectors;
import java.util.function.Function;

public class vRouterConfig extends Config<ApplicationId> {
    // mandatory data fields
    public static final String QUAGGA_LOCATION = "quagga";
    public static final String QUAGGA_MAC = "quagga-mac";
    public static final String VROUTER_IP = "virtual-ip";
    public static final String VROUTER_MAC = "virtual-mac";
    public static final String PEERS = "peers";

    Function<String, String> func = (String e)-> {return e;};

    // Only check the mandatory data fields
    @Override
    public boolean isValid() {
        return hasFields(QUAGGA_LOCATION, QUAGGA_MAC,
                        VROUTER_IP, VROUTER_MAC,
                        PEERS);
    }

    // Retrieve data
    public ConnectPoint getQuaggaConnectPoint() {
        return ConnectPoint.fromString(get(QUAGGA_LOCATION, null));
    }

    public MacAddress getQuaggaMacAddress() {
        return MacAddress.valueOf(get(QUAGGA_MAC, null));
    }

    public IpAddress getVrouterIPAddress() {
        return IpAddress.valueOf(get(VROUTER_IP, null));
    }

    public MacAddress getVrouterMacAddress() {
        return MacAddress.valueOf(get(VROUTER_MAC, null));
    }

    public List<IpAddress> getPeers() {
        // Retrieve the list of IP addresses as strings
        List<String> peers = getList(PEERS, String::new);

        // Convert the list of strings to a list of IpAddress objects using a stream
        return peers.stream()
                    .map(IpAddress::valueOf)
                    .collect(Collectors.toList());
    }
}