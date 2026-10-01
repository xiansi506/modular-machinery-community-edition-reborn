import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Round-trips an existing config file through a ForgeConfigSpec, WITHOUT launching the game.
 *
 * <p>This mirrors what Forge's {@code ConfigTracker}/{@code ConfigFileTypeHandler} do when they load a config:
 * read the TOML into a CommentedFileConfig, run {@code ForgeConfigSpec#isCorrect}/{@code #correct}, hand the file
 * config to the spec with {@code #setConfig}, and read the values back through the spec. The file is also written
 * back with NightConfig's own writer, which is what Forge uses to save.
 *
 * <p>args: &lt;specClass&gt; &lt;specField&gt; &lt;inPath&gt; &lt;outPath&gt;
 */
public final class ConfigRoundTrip {

    private static final String[] KEYS = {
            "parallel-controller.normal.max-parallelism",
            "parallel-controller.reinforced.max-parallelism",
            "parallel-controller.elite.max-parallelism",
            "parallel-controller.super.max-parallelism",
            "parallel-controller.ultimate.max-parallelism",
            "upgrade-bus.normal.max-upgrade_slot",
            "upgrade-bus.reinforced.max-upgrade_slot",
            "upgrade-bus.elite.max-upgrade_slot",
            "upgrade-bus.super.max-upgrade_slot",
            "upgrade-bus.ultimate.max-upgrade_slot" };

    public static void main(String[] args) throws Exception {
        Class<?> owner = Class.forName(args[0]);
        Field specField = owner.getField(args[1]);
        Object spec = specField.get(null);
        java.io.File in = new java.io.File(args[2]);
        java.io.File out = new java.io.File(args[3]);
        out.getParentFile().mkdirs();

        Class<?> formatType = Class.forName("com.electronwill.nightconfig.toml.TomlFormat");
        Object format = formatType.getMethod("instance").invoke(null);
        Class<?> configType = Class.forName("com.electronwill.nightconfig.core.file.CommentedFileConfig");
        Class<?> builderType = Class.forName("com.electronwill.nightconfig.core.file.GenericBuilder");
        Class<?> commentedType = Class.forName("com.electronwill.nightconfig.core.CommentedConfig");
        Object builder = configType
                .getMethod("builder", java.nio.file.Path.class, Class.forName("com.electronwill.nightconfig.core.ConfigFormat"))
                .invoke(null, in.toPath(), format);
        builder = builderType.getMethod("preserveInsertionOrder").invoke(builder);
        Object fileConfig = builderType.getMethod("build").invoke(builder);

        Method get = fileConfig.getClass().getMethod("get", String.class);
        System.out.println("--- file as loaded (before any spec involvement) ---");
        for (String key : KEYS) {
            System.out.println("  raw file value " + key + " = " + get.invoke(fileConfig, key));
        }

        System.out.println("--- spec validation of that file ---");
        System.out.println("  isCorrect(loaded file) = "
                + spec.getClass().getMethod("isCorrect", commentedType).invoke(spec, fileConfig));
        System.out.println("  corrections applied    = "
                + spec.getClass().getMethod("correct", commentedType).invoke(spec, fileConfig));
        System.out.println("  isCorrect(after correct) = "
                + spec.getClass().getMethod("isCorrect", commentedType).invoke(spec, fileConfig));

        spec.getClass().getMethod("setConfig", commentedType).invoke(spec, fileConfig);
        System.out.println("  spec.isLoaded()        = " + spec.getClass().getMethod("isLoaded").invoke(spec));

        System.out.println("--- values read through the spec (what the tier enums see) ---");
        for (String key : KEYS) {
            System.out.println("  spec value " + key + " = " + get.invoke(fileConfig, key));
        }

        spec.getClass().getMethod("save").invoke(spec);
        System.out.println("--- after spec.save(), re-read from the file config ---");
        for (String key : KEYS) {
            System.out.println("  saved value " + key + " = " + invoke(get, fileConfig, key));
        }

        invoke(fileConfig.getClass().getMethod("save"), fileConfig);
        try {
            invoke(fileConfig.getClass().getMethod("close"), fileConfig);
        } catch (Exception e) {
            System.out.println("  (close() unavailable: " + e.getClass().getSimpleName() + ")");
        }
        java.nio.file.Files.copy(in.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("  snapshot copied to " + out.getAbsolutePath());
    }

    /** NightConfig's file-config classes are package-private; open the method before invoking. */
    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            method.setAccessible(true);
            return method.invoke(target, args);
        }
    }
}
