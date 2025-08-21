#define _GNU_SOURCE
#include <stdio.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>

static int (*libc_socket)(int, int, int) = NULL;

__attribute__((constructor)) void init(void) {
  libc_socket = dlsym(RTLD_NEXT, "socket");
  if (!libc_socket) {
    fprintf(stderr, "Failed to find original socket() libc symbol\n");
    exit(1);
  }
}

int socket(int domain, int type, int protocol) {
  int sockfd = libc_socket(domain, type, protocol);
  if (sockfd == -1) {
    fprintf(stderr, "Failed to create socket: %s\n", strerror(errno));
  } else {
    printf("Created socket with fd: %d\n", sockfd);
    // Check if the socket is TCP.
    if ((domain == AF_INET || domain == AF_INET6) &&
        (type & 0xF) == SOCK_STREAM &&
        (protocol == 0 || protocol == IPPROTO_TCP)) {
      // Check if user has set TCP_USER_TIMEOUT_MS to enable the timeout.
      const char *timeout_env = getenv("TCP_USER_TIMEOUT_MS");
      if (timeout_env) {
        int timeout = atoi(timeout_env);
        if (timeout > 0) {
          if (setsockopt(sockfd, IPPROTO_TCP, TCP_USER_TIMEOUT, &timeout, sizeof(timeout)) == -1) {
            fprintf(stderr, "Failed to set TCP_USER_TIMEOUT: %s\n", strerror(errno));
          } else {
            printf("Set TCP_USER_TIMEOUT to %d ms on fd %d\n", timeout, sockfd);
          }
        }
      }
    }
  }
  return sockfd;
}
