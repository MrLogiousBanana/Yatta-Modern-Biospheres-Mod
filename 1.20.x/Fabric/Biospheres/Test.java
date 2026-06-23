public class Test {
    public static void main(String[] args) {
        for (java.lang.reflect.Method m : net.minecraft.registry.RegistryOps.class.getDeclaredMethods()) {
            System.out.println(m);
        }
    }
}
