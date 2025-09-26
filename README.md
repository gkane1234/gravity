# Real-Time N-Body Simulation using Barnes Hut and Compute Shaders
A Barnes Hut Implementation of the N-Body problem capable of handling over 32 million objects in realtime.
All calculations during the simulation are done using compute shaders in GLSL.

## Features:
 1. Full custom graphical UI
 2. Real-time interaction with massive, highly parallelized simulations
 3. Custom algorithms for each step in the process of constructing and evaluating Barnes-Hut n-body.
 4. Accurate star sizes, colors, and scales.
 5. Able to simulate galaxies, solar systems, and other particle systems
 6. Uses generating code to minimize repetition



// 0. Common resources and definitions (bh_common.comp)
//     a. Uniform definitions
//     b. SSBO bindings
//     c. Common definitions of structs and functions
// 1. Initialization (bh_init.comp)
//     a. Init Kernel
// 2. Updating Values (bh_update.comp)
//     a. Update Kernel (acts as two kernels)
//          i. Reset Queues 
//          ii. Update Number of Bodies
// 3. Dead Body Counting (bh_dead.comp)
//     a. Dead Count Kernel
//     b. Dead Exclusive Scan Kernel
//     c. Dead Scatter Kernel
// 4. Morton Encoding (bh_morton.comp)
//     a. Morton AABB Repopulate Kernel
//     b. Morton AABB Collapse Kernel
//     b. Morton Encode Kernel
// 5. Radix Sort (bh_radix.comp)
//     a. Radix Histogram Kernel
//     b. Radix Bucket Scan Kernel
//     c. Radix Global Scan Kernel
//     d. Radix Scatter Kernel
// 6. Tree Building (bh_tree.comp)
//     a. Tree Build Binary Radix Tree Kernel
//     b. Tree Init Leaf Nodes Kernel
//     c. Tree Propagate Nodes Kernel
// 7. Force Computation (bh_force.comp)
//     a. Force Compute Kernel (also updates position and velocity of bodies)
// 8. Merging Bodies (bh_merge.comp)
//     a. Merge Bodies Kernel
// 9. Debugging (bh_debug.comp)
//     a. Debug Kernel
## Algorithm Breakdown
Our implementation of the Barnes-Hut N-Body Algorithm works broadly in 7 steps:

1. 
## Features to implement:

 1. Make a branch with a true octree
 2. Add a UISelector object instead of one effectively existing in the property class
 3. Allow the user to interact with the simulation
 4. -- Add objects to the simulation like galaxies, stars, planets, etc.
 5. Make planets and stars render differently based on their mass/density
 6. Implement a lighting system for the simulation
 7. Find ways to add more planets to the simulation
 8. --For example, get rid of the leafnode buffer, spread nodes/bodies over more buffers
 9. --Combine bodies and nodes into one struct
 10. During radix sort, and AABB reduction, have the first kernel be called twice to reduce by a factor of wg_size^2 before the second kernel is called
 11. We calculate the AABB for the simulation at the beginning and then recalculate it during tree propagation. We don't know if it is possible to avoid this but as an observation.
 12. Futher integrate rendering into the GPU class
     1.  Put render program creation into the GPU class
     2.  Possibly add a new pipline for the actual rendering call
     3.  Create the meshes in the GPU class
 13. Make a units object that handles units and uploads that to the GPU
 14. A way to import meshes
 15. A way to import planet data
 16. Make regions_ssbo be auto generated from compute_ssbo
 17. Have certain java or glsl values be auto generated so there is only one place to change them



## Issues:

 1.  galaxy generation seems to tear itself apart sometimes
 2. the name of fixed ssbo objects is overwritten by the swapping ssbos
 3. Radix sort dispatches with too many workgroups when the number of bodies goes down which matters a lot because of the amount of inop threads
 4. Most shaders dispatch with too many workgroups when the number of bodies goes down
 5. number of merged, number of lost to oob not calculated correctly at times (sometimes number of lost is negative)
 6. Radix bits cannot be changed from 4
 7. Camera is jumpy especially when far away
 8. There is a discrete change between glow and body rendering when moving towards a body


 

