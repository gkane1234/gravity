package com.grumbo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;



public class ChunkList {
    private ArrayList<Chunk> chunks;
    private HashMap<CoordKey, Integer> chunkMap;



    public ChunkList() {
        chunks = new ArrayList<Chunk>();
        chunkMap = new HashMap<CoordKey, Integer>();
    }

    public void addChunk(CoordKey center) {

        addChunk(new Chunk(center));
    }

    public void addChunkWithPlanet(Planet p) {
        // Check if chunk already exists
        CoordKey planetCoordKey = p.chunkCenter;
        if (planetCoordKey == null) {
            // Planet doesn't have a CoordKey yet, create one
            planetCoordKey = new CoordKey(new double[] {p.x, p.y, p.z});
        }
        
        Integer existingIndex = chunkMap.get(planetCoordKey);
        if (existingIndex != null) {
            // Chunk exists, just add the planet to it
            chunks.get(existingIndex).addPlanet(p);
        } else {
            // Chunk doesn't exist, create new one
            addChunk(new Chunk(planetCoordKey));
            // Add the planet to the newly created chunk
            chunks.get(chunks.size() - 1).addPlanet(p);
        }
    }

    public void addChunk(Chunk c) {
        if (chunkMap.containsKey(c.center)) {
            //System.out.println("Chunk already exists at " + c.center.x + "," + c.center.y + "," + c.center.z+" and chunk object "+c);
            throw new RuntimeException("Chunk already exists");
        }
        chunks.add(c);
        chunkMap.put(c.center, chunks.size() - 1);
        //System.out.println("Chunk added at " + c.center.x + "," + c.center.y + "," + c.center.z+" and chunk object "+c);
    }
    
    public void removeChunk(CoordKey center) {
            Integer index = chunkMap.get(center);
            if (index == null) return;
            
            // Safety check: ensure index is valid
            if (index < 0 || index >= chunks.size()) {
                System.err.println("Warning: Invalid chunk index " + index + " for chunk at " + center.x + "," + center.y + "," + center.z);
                return;
            }
            //System.out.println("Removing chunk at " + center.x + "," + center.y + "," + center.z + " with index " + index+ " and number of planets " + chunks.get(index).planets.size()+" and chunk object "+chunks.get(index));
            chunks.remove(index.intValue());
            chunkMap.remove(center);
            
            // Update all indices greater than the removed index
            for (Map.Entry<CoordKey, Integer> entry : chunkMap.entrySet()) {
                if (entry.getValue() > index) {
                    entry.setValue(entry.getValue() - 1);
                }
            }
    }

    public Chunk getChunk(long[] center) {
            Integer index = chunkMap.get(new CoordKey(center[0], center[1], center[2]));
            return index != null ? chunks.get(index) : null;
    }

    public Chunk getChunk(int index) {
            return chunks.get(index);
    }

    public ArrayList<Chunk> getChunks() {
            return new ArrayList<>(chunks);  // Return a copy to prevent concurrent modification
    }

    public int getChunkIndex(CoordKey center) {
            Integer index = chunkMap.get(center);
            return index != null ? index : -1;
    }

    public int getNumChunks() {
            return chunks.size();
    }


}
