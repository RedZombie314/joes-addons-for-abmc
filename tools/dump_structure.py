"""Dump a compressed Minecraft structure NBT (palette + blocks) for inspection."""
import gzip
import struct
import sys

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def u1(self):
        v = self.data[self.pos]
        self.pos += 1
        return v

    def i1(self):
        v = struct.unpack_from('>b', self.data, self.pos)[0]
        self.pos += 1
        return v

    def i2(self):
        v = struct.unpack_from('>h', self.data, self.pos)[0]
        self.pos += 2
        return v

    def i4(self):
        v = struct.unpack_from('>i', self.data, self.pos)[0]
        self.pos += 4
        return v

    def i8(self):
        v = struct.unpack_from('>q', self.data, self.pos)[0]
        self.pos += 8
        return v

    def f4(self):
        v = struct.unpack_from('>f', self.data, self.pos)[0]
        self.pos += 4
        return v

    def f8(self):
        v = struct.unpack_from('>d', self.data, self.pos)[0]
        self.pos += 8
        return v

    def string(self):
        n = struct.unpack_from('>H', self.data, self.pos)[0]
        self.pos += 2
        s = self.data[self.pos:self.pos + n].decode('utf-8')
        self.pos += n
        return s

    def payload(self, tag):
        if tag == TAG_BYTE:
            return self.i1()
        if tag == TAG_SHORT:
            return self.i2()
        if tag == TAG_INT:
            return self.i4()
        if tag == TAG_LONG:
            return self.i8()
        if tag == TAG_FLOAT:
            return self.f4()
        if tag == TAG_DOUBLE:
            return self.f8()
        if tag == TAG_BYTE_ARRAY:
            n = self.i4()
            raw = self.data[self.pos:self.pos + n]
            self.pos += n
            return list(raw)
        if tag == TAG_STRING:
            return self.string()
        if tag == TAG_LIST:
            item = self.u1()
            n = self.i4()
            return [self.payload(item) for _ in range(n)]
        if tag == TAG_COMPOUND:
            out = {}
            while True:
                t = self.u1()
                if t == TAG_END:
                    return out
                name = self.string()
                out[name] = self.payload(t)
        if tag == TAG_INT_ARRAY:
            n = self.i4()
            return [self.i4() for _ in range(n)]
        if tag == TAG_LONG_ARRAY:
            n = self.i4()
            return [self.i8() for _ in range(n)]
        raise ValueError('unknown tag %d' % tag)


def main(path):
    with gzip.open(path, 'rb') as f:
        data = f.read()
    r = Reader(data)
    tag = r.u1()
    name = r.string()
    root = r.payload(tag)
    print('root name=%r keys=%s' % (name, list(root.keys())))
    for k, v in root.items():
        if k in ('palette', 'blocks', 'entities'):
            print('%s: %d entries' % (k, len(v)))
        else:
            print('%s: %r' % (k, v))
    palette = root.get('palette', [])
    print('--- palette ---')
    for i, p in enumerate(palette):
        print(i, p)
    blocks = root.get('blocks', [])
    print('--- blocks (%d) ---' % len(blocks))
    xs, ys, zs = [], [], []
    for b in blocks:
        pos = b['pos']
        xs.append(pos[0])
        ys.append(pos[1])
        zs.append(pos[2])
        print(pos, b.get('state'))
    if blocks:
        print('min=(%d,%d,%d) max=(%d,%d,%d)' % (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)))
    ents = root.get('entities', [])
    print('--- entities (%d) ---' % len(ents))
    for e in ents:
        print(e.get('pos'), e.get('nbt', {}).get('id'))


if __name__ == '__main__':
    main(sys.argv[1])
