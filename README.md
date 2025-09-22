# Barnes Hut Implementation of the N-Body Problem
A Barnes Hut Implementation of the N-Body problem capable of handling over 32 million objects in realtime.
All calculations during the simulation are done using compute shaders in GLSL


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


## Issues:

 1.  galaxy generation seems to tear itself apart sometimes
 2. the name of fixed ssbo objects is overwritten by the swapping ssbos
 3. Radix sort dispatches with too many workgroups when the number of bodies goes down which matters a lot because of the amount of inop threads
 4. Most shaders dispatch with too many workgroups when the number of bodies goes down
 5. number of merged, number of lost to oob not calculated correctly at times (sometimes number of lost is negative)
 6. Radix bits cannot be changed from 4
 7. Camera is jumpy especially when far away


 

