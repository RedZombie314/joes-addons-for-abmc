import zipfile, json, os, glob

base = r"D:\下载（D）\Joe's Addons for ABMC Original"
jar_candidates = sorted(glob.glob(os.path.join(base, "build", "libs", "*.jar")),
                        key=os.path.getmtime)
jar = jar_candidates[-1]
print("jar:", jar, os.path.getmtime(jar))
new = ["take_this", "movement", "dad_bought_milk", "blue_thumb", "easier_to_smelt", "right_on_the_head"]
with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    for n in new:
        p = f"data/joes_addons_for_abmc/advancement/{n}.json"
        ok = p in names
        parent = ""
        if ok:
            parent = json.loads(z.read(p))["parent"]
        print(f"  {n:18s} in_jar={ok} parent={parent}")
    # lang
    zh = json.loads(z.read("assets/joes_addons_for_abmc/lang/zh_cn.json"))
    en = json.loads(z.read("assets/joes_addons_for_abmc/lang/en_us.json"))
    for n in new:
        k = f"advancements.joes_addons_for_abmc.{n}"
        print(f"  {n:18s} zh={zh.get(k+'.title')!r}/{zh.get(k+'.description')!r}  en={en.get(k+'.title')!r}/{en.get(k+'.description')!r}")
    mn = [x for x in names if x.endswith("AdvancementWidgetLineMixin.class")]
    print("  mixin classes:", mn)

# deployed jar
dep = r"E:\.minecraft\versions\生存档（mod版3）\.minecraft\versions\ABMC Mod Testing\mods"
if os.path.isdir(dep):
    js = [f for f in os.listdir(dep) if f.startswith("joes_addons_for_abmc-")]
    print("deployed:", [(f, os.path.getsize(os.path.join(dep, f))) for f in js])
    for f in js:
        with zipfile.ZipFile(os.path.join(dep, f)) as z:
            print("  ", f, "has take_this:", "data/joes_addons_for_abmc/advancement/take_this.json" in z.namelist())
