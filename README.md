# NYCU SDN-NFV 2023

## Project 1 - ONOS and Mininet Environment Setup & Basic Operation

Create hosts, switches, and links

## Project 2 - OpenFlow Protocol Observation and Flow Rule Installation

- Flow Rules
    - Forwarding ARP packets to all port in one instruction
    - Forwarding IPv4 packets bi-directionally
- Topology has broadcast storm
    - Any cycle can cause it
- Reactive forwarding
    - Use ARP and ICMP as example

## Project 3 - SDN-enabled Learning Bridge

- Maintain the MAC-Port forwarding table for hosts
    - Look up MAC address table for destination MAC
    - Updates MAC address table with source MAC and incoming port
- Install the flow rules on the switches

## Project 4 - Unicast DHCP

- DHCP discover, offer, and request are a broadcast message which increases the network workload
- Unicast DHCP app shall make the DHCP clients and switches know where should they send. All DHCP are unicast
- Config Linster for DHCP server location

## Project 5 - Proxy ARP

- ARP Request increase workload of network devices and probability of broadcast storm
- Proxy device like router, firewall, and SDN controller can be a proxy device answers ARP Requests for IP address
- Maintain the IP-MAC (ARP table)

## Project 6 - Software Router and Containerization

- Create containers for virtual routers, hosts. And link them
- Create several AS and connection them by using BGP

## Final Project - SDN Network as Virtual Router

- Based on Project6, create several AS
- One AS doesn't use the virtual router from 3rd. Use Project3 ~ Project5 and handcraft vRouter to form a virtual router
    - Learning Bridge: Intra domain communication
    - Unicast DHCP: DHCP support for devices
    - Proxy ARP: ARP reply for devices
- Virtual router
    - BGP: Forward external router’s eBGP packet to the routing server (e.g., Quagga)
    - Gateway: L2 modification for inter domain (SDN Network <-> Others)
    - Routing: Decide next hop using information collected from Quagga (Other Network <-> Others)
    
