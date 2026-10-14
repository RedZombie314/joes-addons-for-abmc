import zipfile, json, glob, os

base = r"D:\下载（D）\Joe's Addons for ABMC Original"
jar = sorted(glob.glob(os.path.join(base, "build", "libs", "*.jar")), key=os.path.getmtime)[-1]
print("built jar:", os.path.basename(jar))

with zipfile.ZipFile(jar) as z:
    d = json.loads(z.read("data/joes_addons_for_abmc/advancement/witness_the_impossible.json"))
    print("parent      :", d["parent"])
    print("frame/hidden:", d["display"]["frame"], d["display"]["hidden"])
    print("criteria    :", sorted(d["criteria"].keys()))
    print("requirements:", d["requirements"], "-> 两组都要满足 =", len(d["requirements"]) == 2)
    zh = json.loads(z.read("assets/joes_addons_for_abmc/lang/zh_cn.json"))
    en = json.loads(z.read("assets/joes_addons_for_abmc/lang/en_us.json"))
    k = "advancements.joes_addons_for_abmc.witness_the_impossible"
    print("zh:", zh[k + ".title"], "/", zh[k + ".description"])
    print("en:", en[k + ".title"], "/", en[k + ".description"])
    # the other completion paths must still be single-criterion
    for n in ["greed", "get_rid_of_greed", "not_today"]:
        dd = json.loads(z.read(f"data/joes_addons_for_abmc/advancement/{n}.json"))
        print(f"  {n:20s} criteria={list(dd['criteria'])}")

dep = r"E:\.minecraft\versions\生存档（mod版3）\.minecraft\versions\ABMC Mod Testing\mods"
js = sorted(f for f in os.listdir(dep) if f.startswith("joes_addons_for_abmc-"))
print("deployed jars:", js)
for f in js:
    with zipfile.ZipFile(os.path.join(dep, f)) as z:
        dd = json.loads(z.read("data/joes_addons_for_abmc/advancement/witness_the_impossible.json"))
        print(f"  {f}: criteria={sorted(dd['criteria'].keys())} requirements={len(dd['requirements'])}")
print("dist:", sorted(f for f in os.listdir(os.path.join(base, "dist")) if f.startswith("joes_addons_for_abmc-"))[-3:])
