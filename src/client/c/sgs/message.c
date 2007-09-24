/*
 * This file provides an implementation of functions relating to network
 * messages.  Implements functions declared in sgs_message.h.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return 0 upon
 *  success and -1 upon failure, while also setting errno to the specific error
 *  code.
 */

#include "sgs/config.h"
#include <arpa/inet.h>
#include "sgs/id.h"
#include "sgs/error_codes.h"
#include "sgs/message.h"
#include "sgs/protocol.h"

/* for sgs_id_impl_write() */
#include "sgs/private/id_impl.h"

typedef struct sgs_message_impl {
    /** Pointer to the start of the memory reserved for this message. */
    uint8_t* buf;
  
    /**
     * Length of the memory reserved for this message.  This may be larger than
     * the number of bytes in the actual message.  If a method attempts to add
     * data to the message payload but there is no room (as indicated by this
     * variable), that method must fail and not write beyond the memory limit
     * imposed by this value.
     */
    size_t buflen;
  
    /**
     * The number of size of this message (so far).  Since the first 4 bytes of
     * any message contains its length (not including those 4 bytes), this
     * variable is redundant with those bytes but is more convenient to use.
     */
    size_t size;
} sgs_message_impl;

static void update_msg_len(sgs_message *pmsg);

/*
 * sgs_msg_add_arb_content()
 */
int sgs_msg_add_arb_content(sgs_message *pmsg, const uint8_t *content,
    size_t clen)
{
    size_t new_size = pmsg->size + clen;
  
    /** check that this won't make the message too long */
    if (new_size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    /** check that this message has enough room allocated */
    if (new_size > pmsg->buflen) {
        errno = ENOBUFS;
        return -1;
    }
  
    /** copy the content over */
    memcpy(pmsg->buf + pmsg->size, content, clen);
  
    /** update the message size fields (both in the struct and in the data) */
    pmsg->size += clen;
    update_msg_len(pmsg);
  
    return 0;
}

/*
 * sgs_msg_add_fixed_content()
 */
int sgs_msg_add_fixed_content(sgs_message *pmsg, const uint8_t *content,
    size_t clen)
{
    size_t new_size = pmsg->size + clen + 2;  /** 2 bytes for length field */
    uint16_t _uint16_tmp;
  
    if (clen > UINT16_MAX) {
        errno = SGS_ERR_SIZE_ARG_TOO_LARGE;
        return -1;
    }
  
    /** check that this won't make the message too long */
    if (new_size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    /** check that this message has enough room allocated */
    if (new_size > pmsg->buflen) {
        errno = ENOBUFS;
        return -1;
    }
  
    /** copy the content's length over */
    _uint16_tmp = htons(clen);
    memcpy(pmsg->buf + pmsg->size, &_uint16_tmp, 2);
  
    /** copy the content over */
    memcpy(pmsg->buf + pmsg->size + 2, content, clen);
  
    /** update the message size fields (both in the struct and in the data) */
    pmsg->size += clen + 2;
    update_msg_len(pmsg);
  
    return 0;
}

/*
 * sgs_msg_add_id()
 */
int sgs_msg_add_id(sgs_message *msg, const sgs_id *id) {
    int result = sgs_id_impl_write(id, msg->buf + msg->size,
        msg->buflen - msg->size);
    
    if (result == -1) return -1;
    
    msg->size += result;
    return 0;
}

/*
 * sgs_msg_add_uint32()
 */
int sgs_msg_add_uint32(sgs_message *pmsg, uint32_t val) {
    uint32_t converted = htonl(val);
    return sgs_msg_add_arb_content(pmsg, (uint8_t*)&converted, 4);
}

/*
 * sgs_msg_deserialize()
 */
sgs_message* sgs_msg_deserialize(uint8_t *buffer, size_t buflen) {
    sgs_message* result = malloc(sizeof(sgs_message));
    if (result == NULL)
        return NULL;

    uint32_t *ptr;
  
    /** read message-length-field (first 4 bytes) */
    ptr = (uint32_t*)buffer;
    result->size = ntohl(*ptr);
  
    /** account for the 4-bytes holding the length itself */
    result->size += 4;
  
    /** check if buffer is long enough to contain this whole message. */
    if (buflen < result->size) {
        free(result);
        errno = EINVAL;
        return NULL;
    }
  
    result->buf = buffer;
    result->buflen = buflen;
  
    /** invalid message: too big */
    if (result->size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        free(result);
        return NULL;
    }
  
    return result;
}

/*
 * sgs_msg_get_bytes()
 */
const uint8_t *sgs_msg_get_bytes(sgs_message *pmsg) {
    return pmsg->buf;
}

/*
 * sgs_msg_get_data()
 */
const uint8_t *sgs_msg_get_data(sgs_message *pmsg) {
    return pmsg->buf + 7;
}

/*
 * sgs_msg_get_datalen()
 */
size_t sgs_msg_get_datalen(sgs_message *pmsg) {
    return pmsg->size - 7;
}

/*
 * sgs_msg_get_opcode()
 */
uint8_t sgs_msg_get_opcode(sgs_message *pmsg) {
    return pmsg->buf[6];
}

/*
 * sgs_msg_get_service()
 */
uint8_t sgs_msg_get_service(sgs_message *pmsg) {
    return pmsg->buf[5];
}

/*
 * sgs_msg_get_size()
 */
size_t sgs_msg_get_size(sgs_message *pmsg) {
    return pmsg->size;
}

/*
 * sgs_msg_get_version()
 */
uint8_t sgs_msg_get_version(sgs_message *pmsg) {
    return pmsg->buf[4];
}

/*
 * sgs_msg_create()
 */
sgs_message* sgs_msg_create(uint8_t *buffer, size_t buflen,
    sgs_opcode opcode, sgs_service_id service_id)
{
    sgs_message* result;

    /** Buffer is too small to hold any messages (even w/o any payload). */
    if (buflen < 7) {
        errno = ENOBUFS;
        return NULL;
    }

    result = malloc(sizeof(sgs_message));
    if (result == NULL)
        return NULL;
  
    result->buf = buffer;
    result->buflen = buflen;
    result->size = 7;
  
    result->buf[4] = SGS_MSG_VERSION;
    result->buf[5] = service_id;
    result->buf[6] = opcode;
    update_msg_len(result);
  
    return result;
}

void sgs_msg_destroy(sgs_message* msg) {
    free(msg);
}

#ifndef NDEBUG
void sgs_msg_dump(const sgs_message* msg) {
    for (size_t i = 0; i < msg->size; ++i) {
        if (i > 0)
            printf(" ");
        printf("%2.2x", msg->buf[i]);
    }
    printf("\n");
}
#endif /* !NDEBUG */

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * update_msg_len()
 */
static void update_msg_len(sgs_message *pmsg) {
    uint32_t _uint32_tmp = htonl(pmsg->size - 4);
    memcpy(pmsg->buf, &_uint32_tmp, 4);
}
