from mininet.topo import Topo

class Project2_Topo_312552017( Topo ):
    def __init__( self ):
        Topo.__init__( self )
        # Add hosts
        h1 = self.addHost( 'h1' )
        h2 = self.addHost( 'h2' )

        # Add switches
        s1 = self.addSwitch( 's1' )
        s2 = self.addSwitch( 's2' )

        # Add links, host to switch
        self.addLink( h1, s1 )
        self.addLink( h2, s2 )

        # Add links, switch to switch
        self.addLink( s1, s2 )
        self.addLink( s2, s1 )

topos = { 'topo_part3_312552017': Project2_Topo_312552017 }
