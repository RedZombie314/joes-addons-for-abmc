import zipfile, json, glob, os

base = r"D:\下载（D）\Joe's Addons for ABMC Original"
jar = sorted(glob.glob(os.path.join(base, "build", "libs", "*.jar")), key=os.path.getmtime)[-1]
print("jar:", os.path.basename(jar))

with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    def adv(n):
        return json.loads(z.read(f"data/joes_addons_for_abmc/advancement/{n}.json"))
    pages = {}
    for n in ["staff", "avm_dimensions", "avm_mobs"]:
        d = adv(n)
        pages[n] = d
        disp = d["display"]
        print(f"ROOT {n:15s} parent={d.get('parent')!r} title={disp['title']['translate']}")
        print(f"     bg={disp.get('background')} hidden={disp.get('hidden')} icon={disp['icon']['id']}")
    for n in ["more_dimensions", "witch_boss_defeated", "angry_birds", "laser_eye", "take_this"]:
        print(f"  {n:20s} parent={adv(n).get('parent')}")
    zh = json.loads(z.read("assets/joes_addons_for_abmc/lang/zh_cn.json"))
    en = json.loads(z.read("assets/joes_addons_for_abmc/lang/en_us.json"))
    for k in ["avm_dimensions", "avm_mobs"]:
        p = f"advancements.joes_addons_for_abmc.{k}"
        print(f"  lang {k:15s} zh={zh.get(p+'.title')==('AVM的维度' if k=='avm_dimensions' else 'AVM的生物')} en={en.get(p+'.title')!r}")
    print("  mixin present:", "cn/autoforged/joes_addons_for_abmc/mixin/AdvancementWidgetLineMixin.class" in names)

dep = r"E:\.minecraft\versions\生存档（mod版3）\.minecraft\versions\ABMC Mod Testing\mods"
js = sorted(f for f in os.listdir(dep) if f.startswith("joes_addons_for_abmc-"))
print("deployed:", js)
for f in js:
    with zipfile.ZipFile(os.path.join(dep, f)) as z:
        n = z.namelist()
        ok = all(f"data/joes_addons_for_abmc/advancement/{x}.json" in n for x in ["avm_dimensions", "avm_mobs"])
        par = json.loads(z.read("data/joes_addons_for_abmc/advancement/angry_birds.json"))["parent"]
        print(f"  {f}: new roots in jar={ok}, angry_birds.parent={par}")

print("dist:", sorted(f for f in os.listdir(os.path.join(base, "dist")) if f.startswith("joes_addons_for_abmc-"))[-3:])
