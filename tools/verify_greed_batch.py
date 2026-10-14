import zipfile, json, glob, os, re

base = r"D:\下载（D）\Joe's Addons for ABMC Original"
jar = sorted(glob.glob(os.path.join(base, "build", "libs", "*.jar")), key=os.path.getmtime)[-1]
print("jar:", os.path.basename(jar))

expect = {
    "greed": ("joes_addons_for_abmc:avm_mobs", "task", False),
    "get_rid_of_greed": ("joes_addons_for_abmc:greed", "task", False),
    "witness_the_impossible": ("joes_addons_for_abmc:get_rid_of_greed", "challenge", True),
    "not_today": ("joes_addons_for_abmc:greed", "task", False),
}
zh_exp = {
    "greed": ("贪婪的代价", "被幸运核心附体。"),
    "get_rid_of_greed": ("知错就改", "击败贪婪的附庸。"),
    "witness_the_impossible": ("见证奇迹", "目睹幸运核心击杀末影龙、凋灵。"),
    "not_today": ("轮到我不吃这套了", "抱歉，不吃压力。"),
}
en_exp = {
    "greed": ("Greed", "Get possessed."),
    "get_rid_of_greed": ("Get Rid of Greed", "Defeat the greedy."),
    "witness_the_impossible": ("Witness the Impossible", "Witness the greedy defeat the bosses."),
    "not_today": ("Not Today, You're Welcome", "Call an ambulance, but not for me."),
}
bad = 0
with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    zh = json.loads(z.read("assets/joes_addons_for_abmc/lang/zh_cn.json"))
    en = json.loads(z.read("assets/joes_addons_for_abmc/lang/en_us.json"))
    for n, (parent, frame, hidden) in expect.items():
        d = json.loads(z.read(f"data/joes_addons_for_abmc/advancement/{n}.json"))
        disp = d["display"]
        ok = (d["parent"] == parent and disp["frame"] == frame and disp["hidden"] == hidden
              and d["criteria"][list(d["criteria"])[0]]["trigger"] == "minecraft:impossible")
        k = f"advancements.joes_addons_for_abmc.{n}"
        zt, zd = zh.get(k + ".title"), zh.get(k + ".description")
        et, ed = en.get(k + ".title"), en.get(k + ".description")
        zhok = (zt, zd) == zh_exp[n]
        enok = (et, ed) == en_exp[n]
        print(f"{n:24s} parent_ok={ok} icon={disp['icon']['id']:32s} zh_ok={zhok} en_ok={enok}")
        bad += 0 if (ok and zhok and enok) else 1
    # greed must be in the lineless set of the mixin
    mixin = [n for n in names if n.endswith("AdvancementWidgetLineMixin.class")]
    print("mixin class:", mixin)
    refs = [n for n in names if n.endswith(".refmap.json")]
    print("refmaps in jar:", refs)

# deployed
dep = r"E:\.minecraft\versions\生存档（mod版3）\.minecraft\versions\ABMC Mod Testing\mods"
for f in sorted(x for x in os.listdir(dep) if x.startswith("joes_addons_for_abmc-")):
    with zipfile.ZipFile(os.path.join(dep, f)) as z:
        n = z.namelist()
        print(f"deployed {f}: greed+not_today in jar =",
              all(f"data/joes_addons_for_abmc/advancement/{x}.json" in n for x in expect))

# sanity: mixin source still lists greed
mixin_src = open(os.path.join(base, "src/main/java/cn/autoforged/joes_addons_for_abmc/mixin/AdvancementWidgetLineMixin.java"),
                 encoding="utf-8").read()
print("LINELESS contains greed:", '"joes_addons_for_abmc:greed"' in mixin_src)
print("mismatches:", bad)
