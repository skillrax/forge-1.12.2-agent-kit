"""Generate the kit's original six-triangle pyramid and checker PNG; stdlib only.
Usage: python make_sample.py NEW_ASSET_ROOT
The root is the contents of assets/staticmodel, not src/main/resources.
"""
from pathlib import Path
import math
import struct
import sys
import zlib
from st3d import write_mesh


def generate(root):
    root = Path(root)
    if root.exists():
        raise FileExistsError('Choose a new sample output directory')
    a, b, c, d, top = (-.5, 0, -.5), (.5, 0, -.5), (.5, 0, .5), (-.5, 0, .5), (0, 3, 0)
    vertices = []
    for face in [(a, top, b), (b, top, c), (c, top, d), (d, top, a), (a, b, c), (a, c, d)]:
        p, q, r = face
        u, v = [q[i] - p[i] for i in range(3)], [r[i] - p[i] for i in range(3)]
        n = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
        length = math.sqrt(sum(x*x for x in n))
        n = tuple(x/length for x in n)
        for point, uv in zip(face, [(0, 1), (.5, 0), (1, 1)]):
            vertices.append((*point, *uv, *n))
    write_mesh(root / 'models/statue/sample.mesh', [dict(texture='staticmodel:textures/statue/checker.png', cutout=False, vertices=vertices)])
    def chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))
    rows = b''.join(b'\x00' + b''.join(bytes((50, 160, 210, 255) if (x//4+y//4)%2 else (235, 190, 60, 255)) for x in range(16)) for y in range(16))
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')
    texture = root / 'textures/statue/checker.png'
    texture.parent.mkdir(parents=True)
    texture.write_bytes(png)


if __name__ == '__main__':
    generate(sys.argv[1])
    print('Original sample generated')

