main:
	docker compose up -d
	sudo ovs-vsctl add-br ovsMainBridge
	sudo ovs-vsctl set-controller ovsMainBridge tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge ovsMainBridge protocols=OpenFlow14
	sudo ovs-docker add-port ovsMainBridge ovs-eth1 R1 --ipaddress=172.20.0.2/16
	sudo ovs-docker add-port ovsMainBridge ovs-eth1 R2 --ipaddress=172.20.0.3/16
	sudo ovs-docker add-port ovsMainBridge ovs-eth1 R3 --ipaddress=172.20.0.4/16

clean:
	docker compose down
	sudo ovs-vsctl del-br ovsMainBridge
	sudo mn -c