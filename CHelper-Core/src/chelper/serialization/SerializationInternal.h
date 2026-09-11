/**
 * Internal serialization implementation access for in-tree tests.
 *
 * Production callers must include Serialization.h.  The implementation is
 * intentionally kept in Serialization.cpp; this header only makes the same
 * definitions available to tests that exercise Glaze's low-level format
 * adapters directly.
 */

#pragma once

#define CHELPER_SERIALIZATION_HEADER_ONLY
#include "Serialization.cpp"
#undef CHELPER_SERIALIZATION_HEADER_ONLY
