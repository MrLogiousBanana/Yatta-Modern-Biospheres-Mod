import java.util.Random;

public class TestRandom {
    public static void main(String[] args) {
        long seed = 12345;
        int sphereRadius = 50;
        for (int i=0; i<5; i++) {
            int centerX = i * 200;
            int centerZ = i * 200;
            Random r = new Random(seed + centerX + 10000L * centerZ);
            int centerY = (int) ((Math.pow((r.nextFloat() % 0.7) - 0.5, 3) + 0.5)
				* (sphereRadius * 2 - sphereRadius * 4)) + sphereRadius * 2;
            System.out.println("Sphere " + i + ": Y = " + centerY);
        }
    }
}
