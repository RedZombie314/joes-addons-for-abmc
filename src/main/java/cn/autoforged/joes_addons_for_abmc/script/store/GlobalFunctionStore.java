package cn.autoforged.joes_addons_for_abmc.script.store;

import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B4 全局函数库：自定义函数以 JSON 存到游戏根目录
 * {@code joes_addons_for_abmc/functions.json}（非 per-world），实现跨存档共享。
 * <p>
 * 以函数名（取图的 {@link ScriptGraph#getName()}）为键建索引；函数体为一张 {@link ScriptGraph}，
 * 运行时可用 {@link cn.autoforged.joes_addons_for_abmc.script.graph.compile.GraphCompiler#buildFunction} 编译为可执行函数。
 */
public class GlobalFunctionStore {
    private static final String FILE_NAME = "functions.json";

    private final Map<String, ScriptGraph> functions = new LinkedHashMap<>();
    private final Path storageFile;

    private static volatile GlobalFunctionStore instance;

    public static GlobalFunctionStore getInstance() {
        if (instance == null) {
            synchronized (GlobalFunctionStore.class) {
                if (instance == null) {
                    instance = new GlobalFunctionStore();
                }
            }
        }
        return instance;
    }

    private GlobalFunctionStore() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("joes_addons_for_abmc");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        this.storageFile = dir.resolve(FILE_NAME);
    }

    public synchronized void load() {
        functions.clear();
        if (!Files.exists(storageFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            String json = readAll(reader);
            for (ScriptGraph g : ScriptGraphCodec.fromJsonCollection(json)) {
                if (g != null && g.getName() != null && !g.getName().isBlank()) {
                    functions.put(g.getName(), g);
                }
            }
        } catch (Exception e) {
            functions.clear();
        }
    }

    public synchronized void save() {
        try (Writer writer = Files.newBufferedWriter(storageFile)) {
            String json = ScriptGraphCodec.toJsonCollection(new ArrayList<>(functions.values()));
            writer.write(json);
        } catch (Exception ignored) {
        }
    }

    /** 保存或覆盖一个自定义函数（函数名取图 name）。 */
    public synchronized void putFunction(ScriptGraph graph) {
        if (graph == null || graph.getName() == null || graph.getName().isBlank()) {
            return;
        }
        functions.put(graph.getName(), graph);
        save();
    }

    /** 删除指定名称的函数；返回是否存在。 */
    public synchronized boolean removeFunction(String name) {
        boolean removed = functions.remove(name) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** 重命名函数；旧名不存在或新名已占用返回 false。 */
    public synchronized boolean renameFunction(String oldName, String newName) {
        if (oldName == null || newName == null || newName.isBlank()) {
            return false;
        }
        if (oldName.equals(newName)) {
            return true;
        }
        ScriptGraph graph = functions.remove(oldName);
        if (graph == null || functions.containsKey(newName)) {
            if (graph != null) {
                functions.put(oldName, graph);
            }
            return false;
        }
        graph.setName(newName);
        functions.put(newName, graph);
        save();
        return true;
    }

    public synchronized ScriptGraph getFunction(String name) {
        return functions.get(name);
    }

    public synchronized boolean contains(String name) {
        return functions.containsKey(name);
    }

    public synchronized List<String> functionNames() {
        return new ArrayList<>(functions.keySet());
    }

    public synchronized Map<String, ScriptGraph> snapshot() {
        return new LinkedHashMap<>(functions);
    }

    private static String readAll(Reader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}