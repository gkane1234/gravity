#version 430
// =============================================================
//                    Regions fragment shader
// =============================================================


out vec4 fragColor;

in flat int uTreeDepth; // depth of the node
// Colors for the regions
vec3 red =vec3(1.0, 0.0, 0.0);
vec3 green = vec3(0.0, 1.0, 0.0);
vec3 blue = vec3(0.0, 0.4, 0.8);
vec3 purple = vec3(1.0, 0.0, 1.0);
vec3 yellow = vec3(1.0, 1.0, 0.0);
vec3 cyan = vec3(0.0, 1.0, 1.0);
vec3 magenta = vec3(1.0, 0.0, 1.0);
vec3 orange = vec3(1.0, 0.5, 0.0);
vec3 brown = vec3(0.5, 0.25, 0.0);

vec3[] colors = {red, green, blue, purple, yellow, cyan, magenta, orange, brown};
// This fragment shader is used to render the regions.
// Color is chosen based on the depth of the node.
void main() {
	// Constant translucent color for regions
	fragColor = vec4(colors[uTreeDepth%colors.length], 0.1);
}

