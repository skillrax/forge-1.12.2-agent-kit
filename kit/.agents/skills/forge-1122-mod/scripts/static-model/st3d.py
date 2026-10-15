"""Normalized static mesh writer shared by scene exporters and the original sample.
Vertices: x,y,z,u,v,nx,ny,nz. Big endian; texture IDs are ASCII ResourceLocations.
No source-format import, attachment, image conversion, or Minecraft dependency.
"""
import math
from pathlib import Path
import re
import struct


def write_mesh(path, parts):
    if not 1 <= len(parts) <= 32:
        raise ValueError('ST3D requires 1..32 material groups')
    data = bytearray(b'ST3D' + struct.pack('>ii', 1, len(parts)))
    total = 0
    for part in parts:
        texture = part['texture']
        if '..' in texture or not re.fullmatch(r'[a-z0-9_]+:textures/[a-z0-9_/.-]+\.png', texture):
            raise ValueError('Invalid PNG texture resource: ' + texture)
        encoded = texture.encode('ascii')
        if len(encoded) > 65535:
            raise ValueError('Texture resource too long')
        vertices = part['vertices']
        total += len(vertices)
        if len(vertices) < 3 or len(vertices) % 3 or total > 1500000:
            raise ValueError('ST3D requires triangles, at most 1500000 vertices total')
        data.extend(struct.pack('>H', len(encoded)) + encoded)
        data.extend(struct.pack('>?i', bool(part['cutout']), len(vertices)))
        for vertex in vertices:
            if len(vertex) != 8 or not all(math.isfinite(v) for v in vertex):
                raise ValueError('Expected eight finite floats per vertex')
            data.extend(struct.pack('>8f', *vertex))
    path = Path(path)
    if path.exists():
        raise FileExistsError('Choose a new mesh output: ' + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as stream:
        stream.write(data)

