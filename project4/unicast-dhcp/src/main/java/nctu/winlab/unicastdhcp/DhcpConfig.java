package nctu.winlab.unicastdhcp;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config; // config notification package

import org.onosproject.net.ConnectPoint;

public class DhcpConfig extends Config<ApplicationId> {
    public static final String DHCP_LOCATION = "serverLocation";

    @Override
    public boolean isValid() {
        return hasOnlyFields(DHCP_LOCATION);
    }

    // Retrieve connection point (switch) in config.json
    public ConnectPoint getConnectPoint() {

        return ConnectPoint.fromString(get(DHCP_LOCATION, null));
    }

}
