// =============================================================
//                  Common layout and structs
// =============================================================

//To change these, you need to also change them in BarnesHut.java
//Radix sort constants:
const uint RADIX_BITS = 4u;
const uint NUM_BUCKETS = 1u << RADIX_BITS;


//Common structs:
//Representation of a celestial body
struct Body { 
    //position (x,y,z) and mass (w)
    vec4 posMass; 
    //velocity (x,y,z) and density (w)
    vec4 velDensity;};

//Representation of an axis aligned bounding box
struct AABB {
    //minimum corner of the bounding box
    vec3 minCorner;
    //maximum corner of the bounding box
    vec3 maxCorner;
};

//Representation of a node in the radix tree
struct Node {
    //center of mass (x,y,z) and mass (w)
    vec4 comMass;
    //stored as a float[6] to avoid padding
    float[6] aabb;
    //children of the node
    uint childA;
    uint childB;
    //depth of the node
    uint nodeDepth;
    //number of bodies contained in the node
    uint bodiesContained;
    //number of ready children of the node (used in updating the tree)
    uint readyChildren;
    //parent of the node
    uint parentId;
};




