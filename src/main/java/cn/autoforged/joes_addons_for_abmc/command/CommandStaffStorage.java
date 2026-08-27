package cn.autoforged.joes_addons_for_abmc.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandStaffStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<StorageData>() {}.getType();

    private List<String> history;
    private Map<String, List<String>> presets;

    private static volatile CommandStaffStorage instance;

    public static CommandStaffStorage getInstance() {
        if (instance == null) {
            synchronized (CommandStaffStorage.class) {
                if (instance == null) {
                    instance = new CommandStaffStorage();
                    instance.load();
                }
            }
        }
        return instance;
    }

    private CommandStaffStorage() {
        history = new ArrayList<>();
        presets = new LinkedHashMap<>();
    }

    private Path getStoragePath() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("joes_addons_for_abmc");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir.resolve("command_staff_data.json");
    }

    public synchronized void load() {
        Path path = getStoragePath();
        if (!Files.exists(path)) {
            history = new ArrayList<>();
            presets = new LinkedHashMap<>();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            StorageData data = GSON.fromJson(reader, DATA_TYPE);
            if (data != null) {
                history = data.history != null ? data.history : new ArrayList<>();
                presets = data.presets != null ? data.presets : new LinkedHashMap<>();
            }
        } catch (Exception e) {
            history = new ArrayList<>();
            presets = new LinkedHashMap<>();
        }
    }

    public synchronized void save() {
        Path path = getStoragePath();
        StorageData data = new StorageData();
        data.history = new ArrayList<>(history);
        data.presets = new LinkedHashMap<>(presets);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(data, writer);
        } catch (Exception ignored) {
        }
    }

    public synchronized boolean tryRecordCommand(String fullCommand) {
        String normalized = fullCommand.trim();
        if (normalized.isEmpty()) return false;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        history.remove(normalized);
        history.add(normalized);
        while (history.size() > 10) {
            history.remove(0);
        }
        save();
        return true;
    }

    public synchronized List<String> getHistory() {
        return new ArrayList<>(history);
    }

    public synchronized void savePreset(String name, List<String> commands) {
        presets.put(name, new ArrayList<>(commands));
        save();
    }

    public synchronized void deletePreset(String name) {
        presets.remove(name);
        save();
    }

    public synchronized void renamePreset(String oldName, String newName) {
        List<String> commands = presets.remove(oldName);
        if (commands != null) {
            presets.put(newName, commands);
            save();
        }
    }

    public synchronized Map<String, List<String>> getPresets() {
        return new LinkedHashMap<>(presets);
    }

    public synchronized List<String> getPresetCommands(String name) {
        List<String> cmds = presets.get(name);
        return cmds != null ? new ArrayList<>(cmds) : new ArrayList<>();
    }

    private static class StorageData {
        List<String> history;
        Map<String, List<String>> presets;
    }
}
