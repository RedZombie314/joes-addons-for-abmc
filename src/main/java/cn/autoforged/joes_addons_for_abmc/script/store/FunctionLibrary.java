package cn.autoforged.joes_addons_for_abmc.script.store;

import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * B5 函数库服务：在 B4 {@link GlobalFunctionStore} 之上提供带校验的高层 CRUD，
 * 供后续网络层（C）与图形编辑器（E）直接调用。
 * <p>
 * 所有写操作返回错误列表（空列表 = 成功）；创建/更新/删除后会自动做全库
 * 调用关系与循环依赖校验，保证函数库始终处于可编译状态。
 */
public class FunctionLibrary {
    private final GlobalFunctionStore store;

    private static volatile FunctionLibrary instance;

    public static FunctionLibrary getInstance() {
        if (instance == null) {
            synchronized (FunctionLibrary.class) {
                if (instance == null) {
                    instance = new FunctionLibrary();
                }
            }
        }
        return instance;
    }

    private FunctionLibrary() {
        this.store = GlobalFunctionStore.getInstance();
    }

    /** 新建函数。成功返回空列表；否则返回错误。 */
    public List<String> createFunction(String name, ScriptGraph graph) {
        if (name == null || name.isBlank()) {
            return List.of("函数名不能为空");
        }
        if (store.contains(name)) {
            return List.of("函数名已存在: " + name);
        }
        graph.setName(name);
        // 新建函数允许为空（尚未添加节点，也就没有入口节点）。
        // 一旦图里已有节点，才做结构校验，避免空创建被“缺少入口节点”误拒，
        // 导致函数创建失败、后续保存（update）因“函数不存在”而无法进行。
        if (!graph.getNodes().isEmpty()) {
            List<String> errors = FunctionValidator.validateFunction(graph);
            if (!errors.isEmpty()) {
                return errors;
            }
        }
        store.putFunction(graph);
        return List.of();
    }

    /** 更新既有函数。成功后校验全库调用关系。 */
    public List<String> updateFunction(String name, ScriptGraph graph) {
        if (!store.contains(name)) {
            return List.of("函数不存在: " + name);
        }
        graph.setName(name);
        List<String> errors = FunctionValidator.validateFunction(graph);
        if (!errors.isEmpty()) {
            return errors;
        }
        store.putFunction(graph);
        return validateAll();
    }

    /** 删除函数。成功后校验全库（其余函数对该函数的调用将报错）。 */
    public List<String> deleteFunction(String name) {
        if (!store.removeFunction(name)) {
            return List.of("函数不存在: " + name);
        }
        return validateAll();
    }

    /** 重命名函数。成功后校验全库。 */
    public List<String> renameFunction(String oldName, String newName) {
        if (!store.renameFunction(oldName, newName)) {
            return List.of("重命名失败：旧名不存在或新名已被占用");
        }
        return validateAll();
    }

    /** 校验整个函数库（结构 + 调用关系 + 循环依赖）。 */
    public List<String> validateAll() {
        return FunctionValidator.validateLibrary(store.snapshot());
    }

    public ScriptGraph getFunction(String name) {
        return store.getFunction(name);
    }

    public boolean contains(String name) {
        return store.contains(name);
    }

    public List<String> listFunctions() {
        return new ArrayList<>(store.functionNames());
    }
}