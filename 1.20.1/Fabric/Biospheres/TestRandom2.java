public class TestRandom2 {
    public static void main(String[] args) {
        long seed = 0;
        int sphereRadius = 50;
        for (int i=0; i<10; i++) {
            int centerX = i * 200;
            int centerZ = 0;
            java.util.Random r = new java.util.Random(seed + (long)centerX * 341873128712L + (long)centerZ * 132897987541L);
            int centerY = (int) ((Math.pow((r.nextFloat() % 0.7) - 0.5, 3) + 0.5)
				* (sphereRadius * 2 - sphereRadius * 4)) + sphereRadius * 2;
            if (centerY < 64) centerY = 64;
            System.out.println("Sphere " + i + ": Y = " + centerY);
        }
    }
}
