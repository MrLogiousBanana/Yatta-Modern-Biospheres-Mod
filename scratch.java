import java.lang.reflect.Field;

public class scratch {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.world.level.levelgen.RandomState");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println(f.getType().getName() + " " + f.getName());
        }
    }
}
