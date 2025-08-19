# Socket wrapper demo

This project demonstrates the use of a socket wrapper to implement TCP user timeout in a Java application.

## Demo steps

1. Create a Kind Cluster

```
kind create cluster --name tcp-user-timeout
```

2. Compile container image with socket wrapper and TCP client-server application

```
make
```

3. Deploy the client and server

```
kubectl apply -f manifests
```

4. Block TCP

```
sudo nsenter --target $(pgrep -f "java -jar tcpapp.jar server") --net -- tc qdisc add dev eth0 root netem loss 100%
```

5. See client logs to observe the effect of TCP_USER_TIMEOUT


```
kubectl logs -l app=tcp-client -f
```


The logs show that after sending ping the client experiences a timeout after 5 seconds and it attempts to reconnect:

```
09:39:48.228 INFO  [main] i.g.t.tcpapp.TcpApp - Sending: ping
09:39:53.697 WARN  [main] i.g.t.tcpapp.TcpApp - Connection error: Operation timed out
09:39:54.698 INFO  [main] i.g.t.tcpapp.TcpApp - Attempting connection to tcp-server:12345
```

6. Delete the Kind cluster

To clean up the resources, delete the Kind cluster:

```
kind delete cluster --name tcp-user-timeout
```
