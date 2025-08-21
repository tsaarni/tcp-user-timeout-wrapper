# Socket Wrapper Demo

This project demonstrates how to use a socket wrapper to implement TCP user timeout in a Java application.

## Background

When a TCP peer becomes unreachable, the TCP stack starts retransmitting the last segment.
On Linux, the number of retransmissions is controlled by the sysctl parameter `net.ipv4.tcp_retries2` ([man 7 tcp](https://man7.org/linux/man-pages/man7/tcp.7.html)):

       tcp_retries2 (integer; default: 15; since Linux 2.2)

              The maximum number of times a TCP packet is retransmitted
              in established state before giving up.  The default value
              is 15, which corresponds to a duration of approximately
              between 13 to 30 minutes, depending on the retransmission
              timeout.  The RFC 1122 specified minimum limit of 100
              seconds is typically deemed too short.


For many applications, the default retransmission period is too long.
For example, if a database server becomes unreachable, an application may need to detect the failure quickly in order to fail over to a backup server.
See [When TCP sockets refuse to die](https://blog.cloudflare.com/when-tcp-sockets-refuse-to-die/) for more information on the problem.

Modifying the sysctl parameter directly requires elevated permissions, which are typically not available to applications.
In Kubernetes, it is not possible to change `net.ipv4.tcp_retries2` through a pod's `securityContext` ([link](https://kubernetes.io/docs/tasks/administer-cluster/sysctl-cluster/#safe-and-unsafe-sysctls)), unless the cluster administrator adds the sysctl to the kubelet's `--allowed-unsafe-sysctls` parameter on all nodes.

An alternative is to use the `TCP_USER_TIMEOUT` socket option, which allows applications to specify a timeout for TCP retransmissions per socket ([man 7 tcp](https://man7.org/linux/man-pages/man7/tcp.7.html)):

       TCP_USER_TIMEOUT (since Linux 2.6.37)

              This option takes an unsigned int as an argument.  When the
              value is greater than 0, it specifies the maximum amount of
              time in milliseconds that transmitted data may remain
              unacknowledged, or buffered data may remain untransmitted
              (due to zero window size) before TCP will forcibly close
              the corresponding connection and return ETIMEDOUT to the
              application.  If the option value is specified as 0, TCP
              will use the system default.

              Increasing user timeouts allows a TCP connection to survive
              extended periods without end-to-end connectivity.
              Decreasing user timeouts allows applications to "fail
              fast", if so desired.  Otherwise, failure may take up to 20
              minutes with the current system defaults in a normal WAN
              environment.

              This option can be set during any state of a TCP
              connection, but is effective only during the synchronized
              states of a connection (ESTABLISHED, FIN-WAIT-1, FIN-
              WAIT-2, CLOSE-WAIT, CLOSING, and LAST-ACK).  Moreover, when
              used with the TCP keepalive (SO_KEEPALIVE) option,
              TCP_USER_TIMEOUT will override keepalive to determine when
              to close a connection due to keepalive failure.

              The option has no effect on when TCP retransmits a packet,
              nor when a keepalive probe is sent.

              This option, like many others, will be inherited by the
              socket returned by accept(2), if it was set on the
              listening socket.

              Further details on the user timeout feature can be found in
              RFC 793 and RFC 5482 ("TCP User Timeout Option").


This socket option is specific to Linux and is not available in OpenJDK.
It is rarely found in Java applications or libraries, unless they use native code via JNI.

This project provides a way to set `TCP_USER_TIMEOUT` using an `LD_PRELOAD` library.
The library intercepts `socket()` calls and sets the `TCP_USER_TIMEOUT` socket option on all created sockets.
See [`socket_wrapper.c`](socket_wrapper.c) for the implementation.
Alternatively, the timeout could be set by overriding `connect()` (for clients) and/or `bind()` (for servers).

The demo also includes [TCP client-server application](docker/client-server/src/main/java/io/github/tsaarni/tcpapp/TcpApp.java) in Java and Kubernetes [manifests](manifests) for deployment.

## Demo Steps

1. Create a Kind Cluster

    ```
    kind create cluster --name tcp-user-timeout
    ```

2. Build the Container Image

    ```
    make
    ```

    This command compiles the socket wrapper and the TCP client-server application into a container image.
    To compile only the library, use `make libsocket_wrapper.so`.

3. Deploy the Client and Server

    ```
    kubectl apply -f manifests
    ```

4. Simulate Network Failure

    ```
    sudo nsenter --target $(pgrep -f "java -jar tcpapp.jar server") --net -- tc qdisc add dev eth0 root netem loss 100%
    ```

    This command simulates a network failure by intentionally dropping all packets on the server pod.

5. Observe Client Logs

    ```
    kubectl logs -l app=tcp-client -f
    ```

    The logs will show that after sending a ping, the client experiences a timeout after 5 seconds (set via `TCP_USER_TIMEOUT`) and attempts to reconnect:

    ```
    09:39:48.228 INFO  [main] i.g.t.tcpapp.TcpApp - Sending: ping
    09:39:53.697 WARN  [main] i.g.t.tcpapp.TcpApp - Connection error: Operation timed out
    09:39:54.698 INFO  [main] i.g.t.tcpapp.TcpApp - Attempting connection to tcp-server:12345
    ```

6. Delete the Kind Cluster

    To clean up resources, delete the Kind cluster:

    ```
    kind delete cluster --name tcp-user-timeout
    ```
