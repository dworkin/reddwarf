/*
 * This file provides common includes and definitions.
 */

#ifndef SGS_CONFIG_H
#define SGS_CONFIG_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#ifdef WIN32
#include <Winsock2.h>

typedef int socklen_t;
#define EMSGSIZE WSAEMSGSIZE
#define ENOBUFS WSAENOBUFS
#define EINPROGRESS WSAEINPROGRESS
#define ENOTCONN WSAENOTCONN

typedef signed char int8_t;
typedef unsigned char uint8_t;
typedef signed short int16_t;
typedef unsigned short uint16_t;
typedef signed int int32_t;
typedef unsigned int uint32_t;
typedef int ssize_t;
#else /* !WIN32 */
#include <stdint.h>
#include <unistd.h>
#include <poll.h>
#include <arpa/inet.h>
#endif /* !WIN32 */

#ifdef __cplusplus
}
#endif

#endif /* !SGS_CONFIG_H */
