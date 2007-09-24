#ifndef SGS_ID_IMPL_H
#define SGS_ID_IMPL_H  1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"
#include "sgs/id.h"

extern size_t sgs_id_impl_write(const sgs_id* id, uint8_t* buf, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_ID_IMPL_H */
