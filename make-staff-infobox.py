import json, io, re, os

data = json.load(io.open("staff-data.json", encoding="utf-8"))
data.append({"名": "[[幸运方块权杖]]", "blocktype": "<code>lucky_block</code>", "装填物": "[[幸运方块]]",
             "能力": "'''占位'''：有正式名称与模型，但没有能力分支", "触发": "—"})

BT2TEX = {
    "gold_block": "staff_gold", "diamond_block": "staff_diamond", "netherite_block": "staff_netherite",
    "obsidian": "staff_obsidian", "bedrock": "staff_bedrock", "bone_block": "staff_bone", "furnace": "staff_furnace",
    "bell": "staff_bell", "anvil": "staff_anvil", "lapis_block": "staff_lapis", "magma_block": "staff_magma",
    "command_block": "staff_command", "end_portal_frame": "staff_end_portal", "enchanting_table": "staff_enchant",
    "player_head": "staff_steve", "herobrine_head": "staff_herobrine", "dripstone_block": "staff_dripstone",
    "omega": "staff_omega", "cauldron": "staff_cauldron", "brewing_stand": "staff_brewing_stand",
    "crafting_table": "staff_crafting_table", "emerald_block": "staff_emerald", "ice": "staff_ice",
    "iron_block": "staff_iron", "netherrack": "staff_netherrack", "note_block": "staff_noteblock",
    "oak_log": "staff_oak", "piston": "staff_piston", "red_mushroom_block": "staff_red_mushroom",
    "redstone_block": "staff_redstone", "snow_block": "staff_snow", "bee_nest": "staff_bee_nest",
    "amethyst_block": "staff_amethyst", "cobweb": "staff_cobweb", "spawner": "staff_spawner", "tnt": "staff_tnt",
    "lightning_rod": "staff_lightning_rod", "minecraft_game_icon": "staff_minecraft", "lucky_block": "staff_lucky",
    "empty": "staff",
}


def icon_for(bt):
    key = re.sub(r"<[^>]+>", "", bt).strip()
    t = BT2TEX.get(key)
    return ("Staff %s.png" % t.replace("staff_", "").replace("_", " ")) if t else None


def summary(abil, limit=78):
    s = " ".join(abil.split())
    parts = re.split(r"(?<=[；。])", s)
    out = parts[0]
    if len(out) < 26 and len(parts) > 1:
        out += parts[1]
    if len(out) > limit:
        cut = out[:limit]
        for sep in ("；", "，", "、", " "):
            i = cut.rfind(sep)
            if i > 32:
                cut = cut[:i]
                break
        out = cut
    out = out.strip().rstrip("，；、")
    if out.count("'''") % 2:
        out += "'''"
    return out


def infobox(title, rows, icon):
    L = ['{| class="wikitable" style="float:right; width:23em; margin:0 0 1em 1em; font-size:90%;"',
         '|+ style="font-size:115%; font-weight:bold;" | ' + title]
    if icon:
        L += ["|-", '| colspan="2" style="text-align:center;" | [[File:' + icon + '|150px]]']
    for k, v in rows:
        L += ["|-", "! " + k, "| " + v]
    L.append("|}")
    return "\n".join(L)


DUR = "权杖本体与方块各 '''100,000''' 点（优先扣方块耐久）"
ENCH = "等级 '''25'''，可附任意魔咒"

d = json.load(io.open("wiki-dump-final4.json", encoding="utf-8"))["页面"]
os.makedirs("wiki-batch16", exist_ok=True)
done = []
for v in data:
    name = v["名"].replace("[[", "").replace("]]", "").strip()
    if name == "权杖（空白）" or name not in d:
        continue
    bt, block, abil, trig = v["blocktype"], v["装填物"], v["能力"], v["触发"]
    icon = icon_for(bt)
    if block.strip() in ("—", "-", ""):
        blockcell = bt
    else:
        blockcell = "%s（装填 1 个 %s）" % (bt, block)
    rows = [("类型", "[[权杖]]的一个'''方块变体'''"), ("方块类型", blockcell), ("触发", trig),
            ("能力概要", summary(abil)), ("耐久", DUR), ("附魔", ENCH),
            ("物品 ID", "<code>joes_addons_for_abmc:staff</code>")]
    box = infobox(name, rows, icon)
    src = d[name]["内容"]
    m = re.match(r"(?s)^(\s*<!--.*?-->\s*)", src)
    if m:
        new = m.group(1).rstrip("\n") + "\n\n" + box + "\n" + src[m.end():]
    else:
        new = box + "\n\n" + src
    io.open("wiki-batch16/" + name + ".wiki", "w", encoding="utf-8", newline="\n").write(new)
    done.append(name)

# 总览页「权杖」单独一个信息框
rows = [("类型", "本模组的核心道具（'''总览页'''）"),
        ("变体总数", "'''41 种'''（37 种可由方块装填 + 3 种特殊形态 + 空白）"),
        ("装填 / 切换", "另一只手持方块，按'''中键'''（键位名「切换权杖属性」)"),
        ("耐久", DUR), ("附魔", ENCH), ("物品 ID", "<code>joes_addons_for_abmc:staff</code>")]
box = infobox("权杖", rows, "Staff staff.png")
src = d["权杖"]["内容"]
m = re.match(r"(?s)^(\s*<!--.*?-->\s*)", src)
new = (m.group(1).rstrip("\n") + "\n\n" if m else "") + box + "\n" + (src[m.end():] if m else src)
io.open("wiki-batch16/权杖.wiki", "w", encoding="utf-8", newline="\n").write(new)
done.append("权杖")

print("生成 %d 个页面的信息框：\n  %s" % (len(done), "、".join(done)))
