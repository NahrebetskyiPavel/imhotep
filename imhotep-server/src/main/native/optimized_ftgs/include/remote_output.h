#pragma once

#include <stdint.h>
#include "imhotep_native.h"

int write_field_start(struct ftgs_outstream* stream,
                      const char *field_name,
                      const int len,
                      const int term_type);
int write_field_end(struct ftgs_outstream* stream);
int write_stream_end(struct ftgs_outstream* stream);

int write_term_group_stats(const struct session_desc* session,
                           struct tgs_desc* tgs,
                           const uint32_t* restrict groups,
                           const int term_group_count);
int flush_buffer(struct buffered_socket* socket);