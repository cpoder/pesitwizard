# Makefile for Connect:Express integration tools
#
# Requires: TOM_DIR environment variable set to CX installation path

TOM_DIR ?= /home/cpo/cx

CC = gcc
CFLAGS = -I$(TOM_DIR)/itom -Wall
LDFLAGS = -L$(TOM_DIR)/itom -litom -no-pie

TARGETS = cx-setup-partner cx-setup-file

all: $(TARGETS)

cx-setup-partner: cx-setup-partner.c
	$(CC) $(CFLAGS) -o $@ $< $(LDFLAGS)

cx-setup-file: cx-setup-file.c
	$(CC) $(CFLAGS) -o $@ $< $(LDFLAGS)

clean:
	rm -f $(TARGETS)

.PHONY: all clean
