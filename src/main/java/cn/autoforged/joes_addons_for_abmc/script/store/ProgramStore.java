package cn.autoforged.joes_addons_for_abmc.script.store;

import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B4 per-world 程序库：本存档的程序以 JSON 存到世界目录
 * {@code joes_addons_for_abmc/programs.json}，随存档隔离。
 * <p>
 * 由服务器生命周期驱动：启动时用世界根目录 {@link #init(Path)} 并加载，停止时 {@link #save()}。
 * 以程序 id（取 {@link ScriptGraph#getId()}）为键建索引。
 */
public class ProgramStore {

    /** 相对世界根目录的存储子目录，与全局函数库命名保持一致。 */
    public static final String SUBDIR = "joes_addons_for_abmc";
    public static final String FILE_NAME = "programs.json";

    private final Map<String, ScriptGraph> programs = new LinkedHashMap<>();
    private Path storageFile;
    private boolean loaded;

    /** 用世界根目录初始化存储位置并加载。 */
    public synchronized void init(Path worldRoot) {
        if (worldRoot == null) {
            return;
        }
        try {
            Files.createDirectories(worldRoot.resolve(SUBDIR));
        } catch (Exception ignored) {
        }
        this.storageFile = worldRoot.resolve(SUBDIR).resolve(FILE_NAME);
        load();
    }

    public synchronized void load() {
        programs.clear();
        loaded = true;
        if (storageFile == null || !Files.exists(storageFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            String json = readAll(reader);
            for (ScriptGraph g : ScriptGraphCodec.fromJsonCollection(json)) {
                if (g != null && g.getId() != null && !g.getId().isBlank()) {
                    programs.put(g.getId(), g);
                }
            }
        } catch (Exception e) {
            programs.clear();
        }
    }

    public synchronized void save() {
        if (storageFile == null) {
            return;
        }
        // 先写临时文件再原子替换目标文件：避免程序库写入中途留下损坏、或被外部进程（如杀软/另一实例）
        // 锁住 programs.json，导致服务器停止（退出存档）保存程序库时被拖住。
        Path tmp = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                writer.write(ScriptGraphCodec.toJsonCollection(new ArrayList<>(programs.values())));
            }
            Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // 写入/移动失败时清理残留临时文件，保持原文件内容不变
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored2) {
            }
        }
    }

    /** 保存或覆盖一个程序（程序 id 取图 id）。 */
    public synchronized void putProgram(ScriptGraph graph) {
        if (graph == null || graph.getId() == null || graph.getId().isBlank()) {
            return;
        }
        programs.put(graph.getId(), graph);
        save();
    }

    public synchronized ScriptGraph getProgram(String id) {
        return programs.get(id);
    }

    public synchronized boolean contains(String id) {
        return programs.containsKey(id);
    }

    public synchronized boolean removeProgram(String id) {
        boolean removed = programs.remove(id) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized List<String> programIds() {
        return new ArrayList<>(programs.keySet());
    }

    public synchronized Map<String, ScriptGraph> snapshot() {
        return new LinkedHashMap<>(programs);
    }

    public boolean isLoaded() {
        return loaded;
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