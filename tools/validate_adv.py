import json, glob, os, sys

base = r"D:\下载（D）\Joe's Addons for ABMC Original"
adv_dir = os.path.join(base, "src", "main", "resources", "data", "joes_addons_for_abmc", "advancement")
lang_dir = os.path.join(base, "src", "main", "resources", "assets", "joes_addons_for_abmc", "lang")

fails = 0
ids = []
for f in sorted(glob.glob(os.path.join(adv_dir, "*.json"))):
    name = os.path.basename(f)
    try:
        with open(f, encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception as e:
        print("JSON FAIL", name, e); fails += 1; continue
    ids.append(name[:-5])
    parent = data.get("parent", "<root>")
    if "display" not in data:
        print(f"{name[:-5]:22s} parent={parent:42s} (no display)")
        continue
    icon = data["display"]["icon"]
    crit = list(data["criteria"].keys())
    print(f"{name[:-5]:22s} parent={parent:42s} icon={icon.get('id')}/{icon.get('components',{}).get('joes_addons_for_abmc:blocktype','-')} crit={crit}")

# parent existence check
for f in sorted(glob.glob(os.path.join(adv_dir, "*.json"))):
    with open(f, encoding="utf-8") as fh:
        data = json.load(fh)
    p = data.get("parent")
    if p and p.startswith("joes_addons_for_abmc:") and p.split(":")[1] not in ids:
        print("MISSING PARENT", os.path.basename(f), p); fails += 1

# lang check
langs = {}
for f in sorted(glob.glob(os.path.join(lang_dir, "*.json"))):
    try:
        with open(f, encoding="utf-8") as fh:
            langs[os.path.basename(f)] = json.load(fh)
    except Exception as e:
        print("LANG JSON FAIL", os.path.basename(f), e); fails += 1

for f in sorted(glob.glob(os.path.join(adv_dir, "*.json"))):
    with open(f, encoding="utf-8") as fh:
        data = json.load(fh)
    if "display" not in data:
        continue
    tr = data["display"]["title"]["translate"]
    de = data["display"]["description"]["translate"]
    for lname, ldata in langs.items():
        for k in (tr, de):
            if k not in ldata:
                print(f"MISSING LANG {lname}: {k}"); fails += 1

print("\nnew ids present:", all(x in ids for x in ["take_this","movement","dad_bought_milk","blue_thumb","easier_to_smelt","right_on_the_head"]))

# --- multi-page structure check ---
roots = []
parents = {}
displays = {}
for f in sorted(glob.glob(os.path.join(adv_dir, "*.json"))):
    with open(f, encoding="utf-8") as fh:
        d = json.load(fh)
    n = os.path.basename(f)[:-5]
    parents[n] = d.get("parent")
    displays[n] = d.get("display")
for n in ids:
    if parents[n] is None:
        roots.append(n)
print("\nroots:", roots)
for n in roots:
    disp = displays[n]
    if disp is None:
        print(f"  {n:16s} (no display -> no tab, ok if guide-only)")
        continue
    bg = disp.get("background", "<none>")
    print(f"  {n:16s} title={disp['title']['translate'].split('.')[-2]:<16s} hidden={disp.get('hidden')} bg={bg}")

# page membership
def page_of(n):
    seen = set()
    while parents.get(n) is not None:
        n = parents[n].split(":")[1]
        if n in seen:
            return "<cycle>"
        seen.add(n)
    return n

pages = {}
for n in ids:
    pages.setdefault(page_of(n), []).append(n)
for p, members in pages.items():
    print(f"\npage root '{p}': {sorted(members)}")

print("\nfailures:", fails)
