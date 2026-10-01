import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Prints every key path / default / range of one ForgeConfigSpec, WITHOUT launching the game.
 *
 * <p>Run with the mapped Forge jar + official-named MC jar on the classpath; args[0] is the class holding the
 * spec (e.g. com.reborn.modularmachinery.config.ModConfig), args[1] the field name (default "SPEC").
 * Everything goes through reflection, so the same probe can be pointed at the 0.21.0 jar (two specs) and at the
 * 0.21.1 jar (one merged spec) and the two outputs diffed. This is a static/runtime check of what the spec
 * declares -- it does not write or read a config file.
 */
public final class ConfigProbe {

    public static void main(String[] args) throws Exception {
        String holder = args[0];
        String specField = args.length > 1 ? args[1] : "SPEC";
        Class<?> owner = Class.forName(holder);
        Field spec = owner.getField(specField);
        Object built = spec.get(null);
        if (built == null) {
            throw new IllegalStateException(holder + "." + specField + " is null -- was build() called?");
        }
        Object values = noArg(built.getClass(), "getValues").invoke(built);
        java.util.List<Object> children = new java.util.ArrayList<>();
        collect(values, children);
        System.out.println("# " + holder + "." + specField + " -> " + children.size() + " values");
        for (Object value : children) {
            System.out.println("VALUE  " + value);
            System.out.println("  path     = " + path(value));
            System.out.println("  default  = " + String.valueOf(noArg(value.getClass(), "getDefault").invoke(value)));
            printRange(value);
        }
        if (args.length > 2) {
            writeToml(built, args[2]);
        }
    }

    /**
     * Serialises the spec tree with NightConfig's own TOML writer, which is what Forge uses to create the file, so
     * the result is byte-comparable with a real {@code modular_machinery_reborn-common.toml}. No game launch and no
     * file is created by Forge; the spec object is simply rendered.
     */
    private static void writeToml(Object specObject, String outPath) throws Exception {
        java.io.File file = new java.io.File(outPath);
        file.getParentFile().mkdirs();
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("cannot clear " + file);
        }
        Class<?> formatType = Class.forName("com.electronwill.nightconfig.toml.TomlFormat");
        Object format = formatType.getMethod("instance").invoke(null);
        Class<?> configType = Class.forName("com.electronwill.nightconfig.core.file.CommentedFileConfig");
        Class<?> builderType = Class.forName("com.electronwill.nightconfig.core.file.CommentedFileConfigBuilder");
        Class<?> genericBuilder = Class.forName("com.electronwill.nightconfig.core.file.GenericBuilder");
        Object builder = configType.getMethod("builder", java.nio.file.Path.class, Class.forName("com.electronwill.nightconfig.core.ConfigFormat"))
                .invoke(null, file.toPath(), format);
        builder = genericBuilder.getMethod("sync").invoke(builder);
        builder = genericBuilder.getMethod("preserveInsertionOrder").invoke(builder);
        builder = genericBuilder.getMethod("autosave").invoke(builder);
        Class<?> actionType = Class.forName("com.electronwill.nightconfig.core.file.FileNotFoundAction");
        builder = genericBuilder.getMethod("onFileNotFound", actionType)
                .invoke(builder, staticOrEnum(actionType, "CREATE_EMPTY"));
        Class<?> writingMode = Class.forName("com.electronwill.nightconfig.core.io.WritingMode");
        builder = genericBuilder.getMethod("writingMode", writingMode)
                .invoke(builder, staticOrEnum(writingMode, "REPLACE"));
        Object fileConfig = genericBuilder.getMethod("build").invoke(builder);
        // ForgeConfigSpec#setConfig fills the file config from the spec; save() is what Forge calls next.
        specObject.getClass().getMethod("setConfig", Class.forName("com.electronwill.nightconfig.core.CommentedConfig"))
                .invoke(specObject, fileConfig);
        fileConfig.getClass().getMethod("save").invoke(fileConfig);
        fileConfig.getClass().getMethod("close").invoke(fileConfig);
        System.out.println("# TOML written to " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
    }

    /** Walks the spec's value tree (nested UnmodifiableConfigs whose leaves are ConfigValues). */
    private static void collect(Object node, java.util.List<Object> out) throws Exception {
        Object raw = noArg(node.getClass(), "valueMap").invoke(node);
        if (!(raw instanceof java.util.Map<?, ?> map)) {
            return;
        }
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            Object child = entry.getValue();
            if (child == null) {
                continue;
            }
            if (isValue(child)) {
                out.add(child);
                continue;
            }
            try {
                collect(child, out);
            } catch (NoSuchMethodException ignored) {
                // not a nested config; nothing to walk
            }
        }
    }

    /** ForgeConfigSpec's leaves are inner classes named *Value (IntValue, BooleanValue, ...). */
    private static boolean isValue(Object child) {
        if (child.getClass().getName().contains("ConfigValue")) {
            return true;
        }
        return hasNoArg(child.getClass(), "getDefault") && hasNoArg(child.getClass(), "getPath");
    }

    private static boolean hasNoArg(Class<?> type, String name) {
        try {
            noArg(type, name);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Resolves a constant that may be an enum constant or a static field/factory of the same name. */
    private static Object staticOrEnum(Class<?> type, String name) throws Exception {
        if (type.isEnum()) {
            return Enum.valueOf(type.asSubclass(Enum.class), name);
        }
        try {
            return type.getField(name).get(null);
        } catch (NoSuchFieldException e) {
            return type.getMethod(name).invoke(null);
        }
    }

    private static void printRange(Object value) {
        Object range = null;
        if (hasNoArg(value.getClass(), "getRange")) {
            try {
                range = noArg(value.getClass(), "getRange").invoke(value);
            } catch (Exception ignored) {
                // not a ranged value
            }
        }
        if (range == null && hasNoArg(value.getClass(), "getMin") && hasNoArg(value.getClass(), "getMax")) {
            try {
                range = noArg(value.getClass(), "getMin").invoke(value) + " .. "
                        + noArg(value.getClass(), "getMax").invoke(value);
            } catch (Exception ignored) {
                // no bounds
            }
        }
        System.out.println("  range    = " + (range == null ? "(none)" : range));
    }

    /** The dotted key path, taken from ConfigValue#toString when the reflective getPath is unavailable. */
    private static String path(Object value) {
        try {
            Object result = noArg(value.getClass(), "getPath").invoke(value);
            if (result instanceof List<?> parts) {
                StringBuilder sb = new StringBuilder();
                for (Object part : parts) {
                    if (sb.length() > 0) {
                        sb.append('.');
                    }
                    sb.append(part);
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
            // fall through to toString parsing
        }
        String text = String.valueOf(value);
        int open = text.indexOf("path=[");
        if (open < 0) {
            return "(unknown)";
        }
        int close = text.indexOf(']', open);
        return text.substring(open + 6, close).replace(", ", ".");
    }

    private static Method noArg(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(name + " on " + type);
    }
}
