public class TestRandom3 {
    public static void main(String[] args) {
        long seed = 0;
        int sphereRadius = 50;
        for (int i=0; i<10; i++) {
            int centerX = i * 200;
            int centerZ = 0;
            java.util.Random r = new java.util.Random(seed + (long)centerX * 341873128712L + (long)centerZ * 132897987541L);
            float f = r.nextFloat();
            double p1 = f % 0.7;
            double p2 = p1 - 0.5;
            double p3 = Math.pow(p2, 3);
            double p4 = p3 + 0.5;
            double p5 = sphereRadius * 2 - sphereRadius * 4;
            int centerY = (int) (p4 * p5) + sphereRadius * 2;
            System.out.println("Sphere " + i + ": f=" + f + ", p4=" + p4 + ", p5=" + p5 + ", centerY=" + centerY);
        }
    }
}
