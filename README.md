# Real-Time N-Body Simulation using Barnes Hut and Compute Shaders
A Barnes Hut Implementation of the N-Body problem capable of handling over 32 million objects in realtime.
All calculations during the simulation are done using compute shaders in GLSL. The CPU side is written in Java using LWGL

## Features:
 1. Real-time n-body interaction with massive, highly parallelized simulations
 2. Fully custom graphical UI
 3. Custom algorithms spread over several compute shaders
 5. Robust Java debugging code to interact with the GPU
 6. Aliasing of several GPU objects for a simplified work flow in Java
 7. Import real data of planet locations
 8. Accurate star sizes, colors, and scales.
 10. Able to simulate anything including solar systems to galactic mergers and super structures with a fully customizable unit system
 11. Uses generating code to minimize repetition in both Java and GLSL



# Compute Shader Algorithms Breakdown
Our implementation of the Barnes-Hut N-Body Algorithm works broadly in 5 steps:

1. Morton encoding of the objects
2. Radix sort based on Morton code
3. Binary tree of spatial hierarchy
4. Force integration
5. Merging, Collisions, and OOB handling

We send out threads for the gpu to do work in batches known as workgroups. They are of a set size (256 in this project) and can share memory with each other while computing.

Bodies are stored in a list of nodes

## Before:

All objects that are empty are sorted to the end of the list.

This can happen if the object was found to be out of bounds on initialization or on the last frame. It also occurs for half of the bodies involved in a merge.

This is done identically to one pass of the radix sort below with objects either being in a dead or an alive bucket. 

## Morton Encoding:

Morton encoding is a method of creating a space filling curve in N-dimensional space by interweaving each coordinate's binary bits in descending order of significance:

$$x_1y_1z_1x_2y_2z_2x_3y_3z_3\ldots$$ for the binary digits of the x,y, and z coordinates 

Nearby codes are generally near each other in space with regular jumps in their location. The tree building algorithm is able to fully realize this complexity and give an accurate spatial partition.

We find morton codes using the Axis Aligned Bounding Box (AABB) of the simulation. In an unbounded simulation, we calculate a new AABB on each step.

We quantize space into 2^21 units along each axis of the AABB. The coordinates of each body in these units creates their 63 bit Morton code that fits neatly into a Long data type.


## Radix Sort:

Radix sort is done in a highly parallel method with enough workgroups so that there is a thread for each body. It is repeated in its entirety for each digit (bucket) of the number in a specified base.
The goal is for each thread to have a unique place to put the sorted body. It is done over four compute programs:

1. Histogram: Each workgroup creates a histogram of bucket frequencies for the bodies seen by the workgroup
2. Bucket Scan: Each workgroup histogram for a specific bucket is then also saved as a running sum over all workgroups (using the workgroup ID as order)
3. Global Scan: Each total sum is then added together to find the global base, or the starting position of each bucket in the eventually sorted list
4. Scatter: Each body is then placed based on the global base, then the running sum of the bucket across workgroups, and finally its place in its workgroup to ensure a unique position for each body

## Binary Tree:
There are two parts to this algorithm:
First, building the binary tree:
This is a direct copy of the Karras Algorithm from https://research.nvidia.com/sites/default/files/pubs/2012-06_Maximizing-Parallelism-in/karras2012hpg_paper.pdf

Second, we fill the nodes with data of the Center of Mass and Mass of the bodies contained within it:

This is done by first initializing all of the leaf nodes and then adding all nodes with two initialized children to a queue.

Next we repeat this process with the enqueued nodes creating a new queue until we are confident that the root node has been initialized (since this broadly separates bodies into two groups for an evenly distributed cloud of objects and can have deeper trees for outliers, 128 repetitions is done (to reduce the amount of workgroups dispatched, we assume that the amount of workgroups required for this process decreases exponentially after each repetition, meaning generally the last ~80 are done with less than 10 workgroups)

## Force Calculation:

Force is calculated for an object based on its distance to nodes in the tree, starting at the root. At a specific node, we use the COM and Mass to apply force or we enqueue the two children. This is decided by the Acceptance Criterion, $\theta$, which is proportional to the longest side of the AABB (\theta > longest side / distance to COM). This differs from the traditional Barnes Hut implementation which generally uses an Octree, but we have found it is still effective in deciding if a locality is close enough to warrant a more accurate force calculation. During this calculation, if we are at a leaf node, we also check for intersections. Collisions are calculated here, otherwise mergers are added to a task list. This is done using the particular gravitation constant calculated by the set of units provided.


## Merge

Merge items are parallelized and all merges are done. Note, this part of the simulation can be non-deterministic over large time steps because of races between elements in the merge task list and we do not recursively discover merges. On sufficiently small time step the simulation is deterministic.


## Complete Structure:

 0. Common resources and definitions (bh_common.comp)
     a. Uniform definitions
     b. SSBO bindings
     c. Common definitions of structs and functions
 1. Initialization (bh_init.comp)
     a. Init Kernel
 2. Updating Values (bh_update.comp)
     a. Update Kernel (acts as two kernels)
          i. Reset Queues 
          ii. Update Number of Bodies
 3. Dead Body Counting (bh_dead.comp)
     a. Dead Count Kernel
     b. Dead Exclusive Scan Kernel
     c. Dead Scatter Kernel
 4. Morton Encoding (bh_morton.comp)
     a. Morton AABB Repopulate Kernel
     b. Morton AABB Collapse Kernel
     b. Morton Encode Kernel
 5. Radix Sort (bh_radix.comp)
     a. Radix Histogram Kernel
     b. Radix Bucket Scan Kernel
     c. Radix Global Scan Kernel
     d. Radix Scatter Kernel
 6. Tree Building (bh_tree.comp)
     a. Tree Build Binary Radix Tree Kernel
     b. Tree Init Leaf Nodes Kernel
     c. Tree Propagate Nodes Kernel
 7. Force Computation (bh_force.comp)
     a. Force Compute Kernel (also updates position and velocity of bodies)
 8. Merging Bodies (bh_merge.comp)
     a. Merge Bodies Kernel
 9. Debugging (bh_debug.comp)
     a. Debug Kernel



# Java side

The Java side is separated into 5 packages:

1. simulation
2. gpu
3. ui
4. debug
5. record

## Simulation

Logic of the simulation. Dispatches all compute shaders, and runs rendering, and UI. Also contains planet generation, and unit specification for creating the initial conditions of the simulation

## GPU

This handles communication with the GPU, including the creation of Compute Programs, Shaders, Meshes, SSBO's, and the reading of data from the GPU and packing data to the GPU.

## UI

Creates the User interface for the simulation window. Also contains the UI objects, and the settings pane that allows for realtime changing of both Java and GPU side variables.

## Debug

Creates a global debugging object that can write debug files for easy debugging of new code. Especially useful for getting information on buffer and uniform states in the GPU.

## Record

Allows for the recording of the simulation by saving each individual frame as a png.




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
 11. Further integrate rendering into the GPU class
     1.  Put render program creation into the GPU class
     2.  Possibly add a new pipeline for the actual rendering call
     3.  Create the meshes in the GPU class
 12. Make a units object that handles units and uploads that to the GPU
 13. A way to import meshes
 14. A way to import planet data
 15. Make regions_ssbo be auto generated from compute_ssbo
 16. Have certain java or glsl values be auto generated so there is only one place to change them
 17. Add hotkey bindings
 18. Add ability to see information about an object in the simulation



## Issues:

 1.  galaxy generation seems to tear itself apart sometimes
 2. the name of fixed ssbo objects is overwritten by the swapping ssbos
 3. Radix sort dispatches with too many workgroups when the number of bodies goes down which matters a lot because of the amount of inop threads
 4. Most shaders dispatch with too many workgroups when the number of bodies goes down
 5. number of merged, number of lost to oob not calculated correctly at times (sometimes number of lost is negative)
 6. Radix bits cannot be changed from 4
 7. Camera is jumpy especially when far away
 8. There is a discrete change between glow and body rendering when moving towards a body
 9. Rename merge queue to merge tasks



 



