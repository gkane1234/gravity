#version 430
#extension GL_NV_gpu_shader5 : enable

#include "bh_common.glsl"
#include "bh_init.glsl"
#include "bh_morton.glsl"
#include "bh_radix.glsl"
#include "bh_tree.glsl"
#include "bh_reduce.glsl"
#include "bh_force.glsl"
#include "bh_debug.glsl"
#include "bh_merge.glsl"
#include "bh_reset.glsl"
#include "bh_dead.glsl"

void main()
{
#ifdef KERNEL_INIT
    initKernel();
#elif defined(KERNEL_MORTON)
    encodeMortonKernel();
#elif defined(KERNEL_DEAD_COUNT)
    deadCountKernel();
#elif defined(KERNEL_DEAD_EXCLUSIVE_SCAN)
    deadExclusiveScanKernel();
#elif defined(KERNEL_DEAD_SCATTER)
    deadScatterKernel();
#elif defined(KERNEL_RADIX_HIST)
    radixHistogramKernel();
#elif defined(KERNEL_RADIX_PARALLEL_SCAN)
    radixParallelScanKernel();
#elif defined(KERNEL_RADIX_EXCLUSIVE_SCAN)
    radixExclusiveScanKernel();
#elif defined(KERNEL_RADIX_DEAD_EXCLUSIVE_SCAN)
    deadExclusiveScanKernel();
#elif defined(KERNEL_RADIX_SCATTER)
    radixScatterKernel();
#elif defined(KERNEL_RADIX_DEAD_SCATTER)
    deadScatterKernel();
#elif defined(KERNEL_BUILD_BINARY_RADIX_TREE)
    buildBinaryRadixTreeKernel();
#elif defined(KERNEL_INIT_LEAVES)
    initLeafNodesKernel();
#elif defined(KERNEL_RESET)
    resetKernel();
#elif defined(KERNEL_PROPAGATE_NODES)
    propagateNodesKernel();
#elif defined(KERNEL_COMPUTE_FORCE)
    computeForce();
#elif defined(KERNEL_MERGE)
    mergeBodiesKernel();
#elif defined(KERNEL_DEBUG)
    debugKernel();
#else
    // no-op
#endif
}



