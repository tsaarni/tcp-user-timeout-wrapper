CC = gcc
TARGET = libsocket_wrapper.so
CFLAGS = -Wall -Werror -fPIC
LDFLAGS ?= -shared -fPIC -ldl

all: $(TARGET) container load

clean:
	rm -f *.o $(TARGET)

$(TARGET): socket_wrapper.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $(TARGET) $<
	cp $(TARGET) docker/client-server

container:
	docker build -t tcp-user-timeout/client-server:latest docker/client-server

load:
	kind load docker-image tcp-user-timeout/client-server:latest --name tcp-user-timeout
