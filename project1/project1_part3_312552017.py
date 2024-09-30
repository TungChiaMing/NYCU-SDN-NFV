from mininet.topo import Topo

class Project1_Topo_312552017( Topo ):
    def __init__( self ):
        Topo.__init__( self )

        # IP Format
        base_ip = '192.168.0.{}'
        subnet = '/27'

        # Add hosts
        h1 = self.addHost( 'h1', ip=base_ip.format(1) + subnet)
        h2 = self.addHost( 'h2', ip=base_ip.format(2) + subnet)
        h3 = self.addHost( 'h3', ip=base_ip.format(3) + subnet)
        h4 = self.addHost( 'h4', ip=base_ip.format(4) + subnet)
        h5 = self.addHost( 'h5', ip=base_ip.format(5) + subnet)

        # Add switches
        s1 = self.addSwitch( 's1' )
        s2 = self.addSwitch( 's2' )
        s3 = self.addSwitch( 's3' )
        s4 = self.addSwitch( 's4' )
        s5 = self.addSwitch( 's5' )

        # Add links, host to switch
        self.addLink( h1, s1 )
        self.addLink( h2, s2 )
        self.addLink( h3, s3 )
        self.addLink( h4, s4 )
        self.addLink( h5, s5 )

        # Add links, switch to switch
        self.addLink( s1, s2 )
        self.addLink( s3, s2 )
        self.addLink( s4, s2 )
        self.addLink( s5, s2 )

topos = { 'topo_part3_312552017': Project1_Topo_312552017 }