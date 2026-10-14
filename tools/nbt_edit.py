import gzip, struct, sys, json, io

TAG_END = 0; TAG_BYTE = 1; TAG_SHORT = 2; TAG_INT = 3; TAG_LONG = 4
TAG_FLOAT = 5; TAG_DOUBLE = 6; TAG_BYTE_ARRAY = 7; TAG_STRING = 8
TAG_LIST = 9; TAG_COMPOUND = 10; TAG_INT_ARRAY = 11; TAG_LONG_ARRAY = 12

class Reader:
    def __init__(self, data):
        self.f = io.BytesIO(data)
    def byte(self): return struct.unpack(">b", self.f.read(1))[0]
    def ubyte(self): return self.f.read(1)[0]
    def short(self): return struct.unpack(">h", self.f.read(2))[0]
    def ushort(self): return struct.unpack(">H", self.f.read(2))[0]
    def int(self): return struct.unpack(">i", self.f.read(4))[0]
    def long(self): return struct.unpack(">q", self.f.read(8))[0]
    def float(self): return struct.unpack(">f", self.f.read(4))[0]
    def double(self): return struct.unpack(">d", self.f.read(8))[0]
    def string(self):
        n = self.ushort()
        return self.f.read(n).decode("utf-8")
    def byte_array(self):
        n = self.int()
        return list(self.f.read(n))
    def int_array(self):
        n = self.int()
        return [self.int() for _ in range(n)]
    def long_array(self):
        n = self.int()
        return [self.long() for _ in range(n)]

def parse_payload(r, tid):
    if tid == TAG_BYTE: return r.byte()
    if tid == TAG_SHORT: return r.short()
    if tid == TAG_INT: return r.int()
    if tid == TAG_LONG: return r.long()
    if tid == TAG_FLOAT: return r.float()
    if tid == TAG_DOUBLE: return r.double()
    if tid == TAG_BYTE_ARRAY: return r.byte_array()
    if tid == TAG_STRING: return r.string()
    if tid == TAG_LIST:
        et = r.ubyte()
        n = r.int()
        return (et, [parse_payload(r, et) for _ in range(n)])
    if tid == TAG_COMPOUND:
        d = {}
        while True:
            t = r.ubyte()
            if t == TAG_END:
                return d
            name = r.string()
            d[name] = (t, parse_payload(r, t))
    if tid == TAG_INT_ARRAY: return r.int_array()
    if tid == TAG_LONG_ARRAY: return r.long_array()
    raise Exception("unknown tag %d" % tid)

def parse(data):
    r = Reader(data)
    t = r.ubyte()
    name = r.string()
    val = parse_payload(r, t)
    return name, t, val

def write_payload(out, tid, val):
    if tid == TAG_BYTE: out.write(struct.pack(">b", val)); return
    if tid == TAG_SHORT: out.write(struct.pack(">h", val)); return
    if tid == TAG_INT: out.write(struct.pack(">i", val)); return
    if tid == TAG_LONG: out.write(struct.pack(">q", val)); return
    if tid == TAG_FLOAT: out.write(struct.pack(">f", val)); return
    if tid == TAG_DOUBLE: out.write(struct.pack(">d", val)); return
    if tid == TAG_BYTE_ARRAY:
        out.write(struct.pack(">i", len(val))); out.write(bytes(val)); return
    if tid == TAG_STRING:
        b = val.encode("utf-8"); out.write(struct.pack(">H", len(b))); out.write(b); return
    if tid == TAG_LIST:
        et, items = val
        out.write(struct.pack(">B", et))
        out.write(struct.pack(">i", len(items)))
        for it in items:
            write_payload(out, et, it)
        return
    if tid == TAG_COMPOUND:
        for k, (ct, cv) in val.items():
            out.write(struct.pack(">B", ct))
            kb = k.encode("utf-8"); out.write(struct.pack(">H", len(kb))); out.write(kb)
            write_payload(out, ct, cv)
        out.write(struct.pack(">B", TAG_END))
        return
    if tid == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(val)))
        for x in val: out.write(struct.pack(">i", x))
        return
    if tid == TAG_LONG_ARRAY:
        out.write(struct.pack(">i", len(val)))
        for x in val: out.write(struct.pack(">q", x))
        return
    raise Exception("unknown tag %d" % tid)

def serialize(name, tid, val):
    out = io.BytesIO()
    out.write(struct.pack(">B", tid))
    nb = name.encode("utf-8"); out.write(struct.pack(">H", len(nb))); out.write(nb)
    write_payload(out, tid, val)
    return out.getvalue()

def load(path):
    with open(path, "rb") as f:
        return parse(gzip.decompress(f.read()))

def save(path, name, tid, val):
    raw = serialize(name, tid, val)
    with open(path, "wb") as f:
        f.write(gzip.compress(raw))

if __name__ == "__main__":
    mode = sys.argv[1]
    path = sys.argv[2]
    name, tid, val = load(path)
    # root should be a compound
    assert tid == TAG_COMPOUND, "root not compound"
    root = val

    # passive -> hostile mapping
    replace_map = {
        "minecraft:chicken_spawn_egg": "minecraft:zombie_spawn_egg",
        "minecraft:parrot_spawn_egg": "minecraft:phantom_spawn_egg",
        "minecraft:glow_squid_spawn_egg": "minecraft:guardian_spawn_egg",
        "minecraft:tropical_fish_spawn_egg": "minecraft:drowned_spawn_egg",
        "minecraft:allay_spawn_egg": "minecraft:vex_spawn_egg",
    }

    def items_changes(compound):
        # compound is dict name -> (tid, value)
        # iterate recursively to find "Items" TAG_LIST of compounds
        changed = 0
        stack = [compound]
        while stack:
            c = stack.pop()
            for k, (ct, cv) in c.items():
                if ct == TAG_COMPOUND:
                    stack.append(cv)
        return changed

    # just dump chest-related palette entries
    def walk_print(c, path_str=""):
        for k, (ct, cv) in c.items():
            p = path_str + "/" + k
            if ct == TAG_STRING and "_spawn_egg" in str(cv):
                print("SPAWN_EGG at", p, "=", cv)
            if ct == TAG_COMPOUND:
                walk_print(cv, p)
            if ct == TAG_LIST:
                et, items = cv
                if et == TAG_COMPOUND:
                    for i, it in enumerate(items):
                        walk_print(it, p + "[" + str(i) + "]")

    if mode == "dump":
        walk_print(root)
    else:
        # replace strings recursively
        def replace_rec(c):
            n = 0
            for k, (ct, cv) in c.items():
                if ct == TAG_STRING:
                    if cv in replace_map:
                        c[k] = (ct, replace_map[cv])
                        n += 1
                elif ct == TAG_COMPOUND:
                    n += replace_rec(cv)
                elif ct == TAG_LIST:
                    et, items = cv
                    if et == TAG_COMPOUND:
                        for it in items:
                            n += replace_rec(it)
                    elif et == TAG_STRING:
                        for i in range(len(items)):
                            if items[i] in replace_map:
                                items[i] = replace_map[items[i]]
                                n += 1
            return n
        n = replace_rec(root)
        save(path, name, tid, root)
        print("replaced", n, "spawn eggs")