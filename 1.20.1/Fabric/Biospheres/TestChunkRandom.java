import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;

public class TestChunkRandom {
    public static void main(String[] args) {
        long seed = 12345;
        int sphereRadius = 50;
        for (int i=0; i<5; i++) {
            int centerX = i * 200;
            int centerZ = 0;
            ChunkRandom r = new ChunkRandom(new Xoroshiro128PlusPlusRandom(0));
            r.setSeed(seed + (long)centerX * 341873128712L + (long)centerZ * 132897987541L);
            float f = r.nextFloat();
            int centerY = (int) ((Math.pow((f % 0.7) - 0.5, 3) + 0.5)
				* (sphereRadius * 2 - sphereRadius * 4)) + sphereRadius * 2;
            System.out.println("Sphere " + i + ": float = " + f + ", Y = " + centerY);
        }
    }
}
