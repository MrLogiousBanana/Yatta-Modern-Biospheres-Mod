import java.util.Random;

public class TestRandom4 {
    public static void main(String[] args) {
        long seed = 12345;
        double sphereRadiusMax = 75;
        double sphereRadiusMin = 10;
        for (int i=0; i<5; i++) {
            int centerX = i * 175;
            int centerY = 63;
            // simulate setCarverSeed
            Random r = new Random(seed + centerX + 10000L * centerY);
            int radius = r.nextInt((int) (sphereRadiusMax - sphereRadiusMin)) + (int)sphereRadiusMin;
            System.out.println("Sphere " + i + ": radius = " + radius);
        }
    }
}
