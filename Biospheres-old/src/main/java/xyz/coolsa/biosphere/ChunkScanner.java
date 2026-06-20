package xyz.coolsa.biosphere;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.WorldChunk;

public class ChunkScanner {
    public BlockPos pos0;
    public BlockPos pos1;
    public BlockPos pos2;
    public BlockPos pos3;
    public ChunkScanner(Chunk chunkNegativeOne) {
        pos0 = chunkNegativeOne.getPos().getBlockPos(0, 0, 0);
        pos1 = chunkNegativeOne.getPos().getBlockPos(0, 0, 15);
        pos2 = chunkNegativeOne.getPos().getBlockPos(15, 0, 0);
        pos3 = chunkNegativeOne.getPos().getBlockPos(15, 0, 15);
    }
    public boolean scanForBiomeCategory(ChunkRegion region, Biome.Category category) {
        BlockPos chunkCenter = new BlockPos(region.getCenterPos().x * 16, 0, region.getCenterPos().z * 16);
        if (region.getBiome(pos0).getCategory() == category) {
            return true;
        } else if (region.getBiome(pos1).getCategory() == category) {
            return true;
        } else if (region.getBiome(pos2).getCategory() == category) {
            return true;
        } else if (region.getBiome(pos3).getCategory() == category) {
            return true;
        } else return region.getBiome(chunkCenter).getCategory() == category;
    }
}
