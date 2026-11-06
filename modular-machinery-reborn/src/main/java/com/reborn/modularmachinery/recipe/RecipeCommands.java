package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The original recipe's three command lists: {@code startCommands}, {@code processingCommands} and
 * {@code finishCommands} (1.12.2 {@code MachineRecipe.java:312-324}, {@code RecipeCommandContainer}).
 *
 * <p>A command is either a plain string or an object {@code {"command": "...", "interval": N}}. The original
 * ran a plain command on every trigger, and an interval command only when {@code tick % interval == 0}; start
 * and finish commands always ran with tick 0. This port keeps that timing exactly, with one deliberate
 * deviation: an interval of {@code 0} runs every time instead of dividing by zero. A malformed datapack must
 * not be able to stop the server.
 *
 * <p>The class deliberately knows nothing about Minecraft's command source. {@link CommandSink} is the single
 * seam the controller implements with a {@code CommandSourceStack}; that keeps parsing, interval timing and
 * the three phases testable offline.
 */
public final class RecipeCommands {

    public static final RecipeCommands EMPTY = new RecipeCommands(List.of(), List.of(), List.of());

    /** The lifecycle point a command list belongs to. */
    public enum Phase {
        START,
        PROCESSING,
        FINISH
    }

    /** Executes one command string. The controller supplies the command source and permission level. */
    @FunctionalInterface
    public interface CommandSink {
        void run(String command);
    }

    /** One command and its optional interval, mirroring the original {@code RecipeRunnableCommand}. */
    public static final class RecipeCommand {

        private final String command;
        private final int interval;

        public RecipeCommand(String command) {
            this(command, -1);
        }

        public RecipeCommand(String command, int interval) {
            this.command = command;
            this.interval = Math.max(-1, interval);
        }

        public String command() {
            return this.command;
        }

        public int interval() {
            return this.interval;
        }

        public boolean hasInterval() {
            return this.interval != -1;
        }

        /** Whether this command runs on the given craft tick. */
        public boolean shouldRun(int tick) {
            return !hasInterval() || this.interval <= 0 || tick % this.interval == 0;
        }

        /** Runs this command if its interval allows it. */
        public void run(int tick, CommandSink sink) {
            if (shouldRun(tick)) {
                sink.run(this.command);
            }
        }
    }

    private final List<RecipeCommand> startCommands;
    private final List<RecipeCommand> processingCommands;
    private final List<RecipeCommand> finishCommands;

    public RecipeCommands(List<RecipeCommand> startCommands, List<RecipeCommand> processingCommands,
                          List<RecipeCommand> finishCommands) {
        this.startCommands = List.copyOf(startCommands);
        this.processingCommands = List.copyOf(processingCommands);
        this.finishCommands = List.copyOf(finishCommands);
    }

    public static RecipeCommands parse(JsonObject json) {
        return new RecipeCommands(parseList(json, "startCommands"),
                parseList(json, "processingCommands"), parseList(json, "finishCommands"));
    }

    private static List<RecipeCommand> parseList(JsonObject json, String tag) {
        if (!json.has(tag)) {
            return List.of();
        }
        JsonElement element = json.get(tag);
        if (!element.isJsonArray()) {
            throw new JsonParseException(tag + " should be an array of commands!");
        }
        JsonArray array = element.getAsJsonArray();
        List<RecipeCommand> commands = new ArrayList<>();
        for (JsonElement commandElement : array) {
            commands.add(parseCommand(commandElement));
        }
        return List.copyOf(commands);
    }

    private static RecipeCommand parseCommand(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new RecipeCommand(element.getAsString());
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (!object.has("command") || !object.get("command").isJsonPrimitive()) {
                throw new JsonParseException(
                        "Tried to deserialize recipe command, found no 'command' string in command object!");
            }
            String command = object.get("command").getAsString();
            if (!object.has("interval")) {
                return new RecipeCommand(command);
            }
            JsonElement interval = object.get("interval");
            if (!interval.isJsonPrimitive() || !interval.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException(
                        "Interval specified in 'interval' of recipe command object is not an int!");
            }
            return new RecipeCommand(command, com.reborn.modularmachinery.util.JsonNumbers.integer(interval,
                    -1, Integer.MAX_VALUE, "recipe command interval"));
        }
        throw new JsonParseException("Tried to deserialize recipe command, found neither command nor object "
                + "describing command and optional interval!");
    }

    public List<RecipeCommand> startCommands() {
        return this.startCommands;
    }

    public List<RecipeCommand> processingCommands() {
        return this.processingCommands;
    }

    public List<RecipeCommand> finishCommands() {
        return this.finishCommands;
    }

    public boolean isEmpty() {
        return this.startCommands.isEmpty() && this.processingCommands.isEmpty() && this.finishCommands.isEmpty();
    }

    /** Runs the list belonging to {@code phase} at {@code tick}. */
    public void run(Phase phase, int tick, CommandSink sink) {
        List<RecipeCommand> commands = switch (phase) {
            case START -> this.startCommands;
            case PROCESSING -> this.processingCommands;
            case FINISH -> this.finishCommands;
        };
        for (RecipeCommand command : commands) {
            command.run(tick, sink);
        }
    }

    public void runStart(CommandSink sink) {
        run(Phase.START, 0, sink);
    }

    public void runProcessing(int tick, CommandSink sink) {
        run(Phase.PROCESSING, tick, sink);
    }

    public void runFinish(CommandSink sink) {
        run(Phase.FINISH, 0, sink);
    }

    public void write(FriendlyByteBuf buffer) {
        writeList(buffer, this.startCommands);
        writeList(buffer, this.processingCommands);
        writeList(buffer, this.finishCommands);
    }

    private static void writeList(FriendlyByteBuf buffer, List<RecipeCommand> commands) {
        buffer.writeVarInt(commands.size());
        for (RecipeCommand command : commands) {
            buffer.writeUtf(command.command());
            buffer.writeVarInt(command.interval());
        }
    }

    public static RecipeCommands read(FriendlyByteBuf buffer) {
        return new RecipeCommands(readList(buffer), readList(buffer), readList(buffer));
    }

    private static List<RecipeCommand> readList(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<RecipeCommand> commands = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            commands.add(new RecipeCommand(buffer.readUtf(), buffer.readVarInt()));
        }
        return List.copyOf(commands);
    }
}
