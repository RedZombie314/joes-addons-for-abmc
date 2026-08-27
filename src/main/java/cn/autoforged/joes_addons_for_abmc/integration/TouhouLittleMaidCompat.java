package cn.autoforged.joes_addons_for_abmc.integration;

import cn.autoforged.joes_addons_for_abmc.task.TaskStaffAttack;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

/**
 * 车万女仆 (Touhou Little Maid) 联动扩展。
 * <p>
 * 通过 {@link LittleMaidExtension} 注解被车万女仆扫描发现，
 * 在 addMaidTask 中注册本 mod 自定义的女仆职业。
 * 未安装车万女仆时本类不会被加载，不影响原 mod 运行。
 */
@LittleMaidExtension
public class TouhouLittleMaidCompat implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new TaskStaffAttack());
    }
}
