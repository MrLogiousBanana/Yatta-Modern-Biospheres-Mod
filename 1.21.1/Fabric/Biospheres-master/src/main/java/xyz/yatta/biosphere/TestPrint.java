package xyz.yatta.biosphere;
public class TestPrint {
    static {
        for (java.lang.reflect.Method m : net.minecraft.registry.RegistryOps.class.getDeclaredMethods()) {
            System.out.println("OPS METHOD: " + m.getName());
        }
        for (java.lang.reflect.Method m : net.minecraft.registry.RegistryCodecs.class.getDeclaredMethods()) {
            System.out.println("CODECS METHOD: " + m.getName());
        }
    }
}
