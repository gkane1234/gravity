# Real-Time N-Body Simulation using Barnes Hut and Compute Shaders
A Barnes Hut Implementation of the N-Body problem capable of handling over 32 million objects in realtime.
All calculations during the simulation are done using compute shaders in GLSL.


## Features to implement:

 1. Make a branch with a true octree
 2. Allow the user to interact with the simulation
 3. -- Add objects to the simulation like galaxies, stars, planets, etc.
 4. Make planets and stars render differently based on their mass/density
 5. Implement a lighting system for the simulation
 6. Find ways to add more planets to the simulation
 7. --For example, get rid of the leafnode buffer, spread nodes/bodies over more buffers
 8. --Combine bodies and nodes into one struct
 9.  During radix sort, and AABB reduction, have the first kernel be called twice to reduce by a factor of wg_size^2 before the second kernel is called
 10. We calculate the AABB for the simulation at the beginning and then recalculate it during tree propagation. We don't know if it is possible to avoid this but as an observation.
 11. Futher integrate rendering into the GPU class
     1.  Put render program creation into the GPU class
     2.  Possibly add a new pipline for the actual rendering call
     3.  Create the meshes in the GPU class
 12. Make a units object that handles units and uploads that to the GPU
 13. A way to import meshes
 14. A way to import planet data
 15. Make regions_ssbo be auto generated from compute_ssbo
 16. Have certain java or glsl values be auto generated so there is only one place to change them



## Issues:

 1.  galaxy generation seems to tear itself apart sometimes
 2. the name of fixed ssbo objects is overwritten by the swapping ssbos
 3. Radix sort dispatches with too many workgroups when the number of bodies goes down which matters a lot because of the amount of inop threads
 4. Most shaders dispatch with too many workgroups when the number of bodies goes down
 5. number of merged, number of lost to oob not calculated correctly at times (sometimes number of lost is negative)
 6. Radix bits cannot be changed from 4
 7. Camera is jumpy especially when far away
 8. There is a discrete change between glow and body rendering when moving towards a body


 

