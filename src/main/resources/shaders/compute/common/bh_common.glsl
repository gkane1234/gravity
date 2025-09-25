// =============================================================
//                          Compute Common
// =============================================================
layout(local_size_x = 256u) in;
const uint WG_SIZE = 256u; // Must match local_size_x above and WORK_GROUP_SIZE in Java

#include "common/common_layout_and_structs.glsl"

#include "compute/common/bh_ssbo.glsl"

#include "common/common_func_uniform_consts.glsl"