#version 430
#extension GL_NV_gpu_shader5 : enable

#include "bh_common.glsl"
#include "bh_aabb.glsl"
#include "bh_morton.glsl"
#include "bh_radix.glsl"
#include "bh_tree.glsl"
#include "bh_reduce.glsl"
#include "bh_force.glsl"
#include "bh_debug.glsl"

void main()
{
#ifdef KERNEL_COMPUTE_AABB
    computeNewAABBKernel();
#elif defined(KERNEL_COLLAPSE_AABB)
    collapseAABBKernel();
#elif defined(KERNEL_MORTON)
    encodeMortonKernel();
#elif defined(KERNEL_RADIX_HIST)
    radixHistogramKernel();
#elif defined(KERNEL_RADIX_PARALLEL_SCAN)
    radixParallelScanKernel();
#elif defined(KERNEL_RADIX_EXCLUSIVE_SCAN)
    radixExclusiveScanKernel();
#elif defined(KERNEL_RADIX_SCATTER)
    radixScatterKernel();
#elif defined(KERNEL_BUILD_BINARY_RADIX_TREE)
    buildBinaryRadixTreeKernel();
#elif defined(KERNEL_INIT_LEAVES)
    initLeafNodesKernel();
#elif defined(KERNEL_PROPAGATE_NODES)
    propagateNodesKernel();
#elif defined(KERNEL_COMPUTE_FORCE)
    computeForce();
#elif defined(KERNEL_DEBUG)
    debugKernel();
#else
    // no-op
#endif
}


