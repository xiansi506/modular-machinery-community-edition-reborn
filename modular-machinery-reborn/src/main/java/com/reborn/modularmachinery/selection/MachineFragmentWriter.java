package com.reborn.modularmachinery.selection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writing the exported fragment into the machinery directory, under the original's own name.
 *
 * <h2>The original's scheme, with line references</h2>
 *
 * <pre>
 * PlayerStructureSelectionHelper.java:103-112
 *     timestampAppend = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
 *     fileName        = "machine-" + player.getName() + "-" + timestampAppend;
 *     machineOut      = new File(directory, fileName + ".json");
 *     int increment   = 0;
 *     while (machineOut.exists()) {
 *         machineOut = new File(directory, fileName + " (" + increment + ").json");
 *         increment++;
 *     }
 * </pre>
 *
 * <p>So the first collision is spelled <b>{@code " (0)"}</b>, not {@code " (1)"} — the counter starts at zero and
 * is incremented <i>after</i> it has been used. Two finalizes within the same second therefore produce
 * {@code machine-Alice-2026-10-02_03.04.05.json} and {@code machine-Alice-2026-10-02_03.04.05 (0).json}; a third
 * produces {@code  (1)}.
 *
 * <p>{@code PlayerStructureSelectionHelper.java:114-124} wrote with {@code Files.write(…, StandardCharsets.UTF_8)}
 * and deleted a half-written file on failure. Both are kept.
 *
 * <p>The whole class takes the directory as a parameter rather than resolving
 * {@code FMLPaths.CONFIGDIR} itself, so the scheme is drivable offline (harness section AA7) — the same reason
 * {@code MachineDirectory.scanMachinePaths} has an explicit-root overload.
 */
public final class MachineFragmentWriter {

    /** The original's {@code SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")}, locale-independent. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    private MachineFragmentWriter() {
    }

    /** {@code machine-<player>-<yyyy-MM-dd_HH.mm.ss>.json} — the original's name, extension included. */
    public static String fileNameFor(String playerName, LocalDateTime timestamp) {
        return "machine-" + playerName + "-" + TIMESTAMP.format(timestamp) + ".json";
    }

    /**
     * Writes {@code json} into {@code directory} under {@code fileName}, adding the original's collision suffix
     * when that name is taken, and returns the file that was written.
     *
     * <p>The machinery directory is created when it does not exist yet: an author's first click on a server with
     * no config directory would otherwise fail with a bare {@code NoSuchFileException}, and creating the
     * directory the loader already reads is not a change of behaviour — the loader treats a missing directory and
     * an empty one identically ({@code MachineDirectory.readAll}).
     */
    public static Path write(Path directory, String fileName, String json) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName);
        int increment = 0;
        while (Files.exists(target)) {
            target = directory.resolve(withSuffix(fileName, " (" + increment + ")"));
            increment++;
        }
        try {
            Files.writeString(target, json, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // The original deleted the incomplete file rather than leaving it behind for the loader to trip over.
            Files.deleteIfExists(target);
            throw exception;
        }
        return target;
    }

    /** {@code machine-x.json} + {@code " (0)"} → {@code machine-x (0).json}. */
    private static String withSuffix(String fileName, String suffix) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName + suffix : fileName.substring(0, dot) + suffix + fileName.substring(dot);
    }
}
