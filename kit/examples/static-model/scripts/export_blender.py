"""Blender 4.2 prepared-scene adapter; no automatic foreign-format import.
blender -b prepared.blend --python export_blender.py -- inspect
blender -b prepared.blend --python export_blender.py -- export CONFIG NEW_OUTPUT
CONFIG names meshes/material slots; NEW_OUTPUT holds sample.mesh + report.json.
"""
import hashlib
import json
import math
from pathlib import Path
import sys
import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
from st3d import write_mesh


def inspect_scene():
    return {'blender': bpy.app.version_string, 'scene': bpy.data.filepath,
            'meshes': [{'object': o.name, 'vertices': len(o.data.vertices),
                        'materials': [slot.material.name if slot.material else None for slot in o.material_slots],
                        'uvLayers': [uv.name for uv in o.data.uv_layers],
                        'modifiers': [m.type for m in o.modifiers],
                        'worldMatrix': [list(row) for row in o.matrix_world]}
                       for o in bpy.context.scene.objects if o.type == 'MESH']}


def export_scene(config_path, output):
    config_path, output = Path(config_path).resolve(), Path(output).resolve()
    if output.exists():
        raise FileExistsError('Choose a new export directory')
    config = json.loads(config_path.read_text(encoding='utf-8'))
    height = float(config['heightBlocks'])
    if not math.isfinite(height) or height <= 0:
        raise ValueError('heightBlocks must be finite and positive')
    objects = config['objects']
    if not objects or len({o['name'] for o in objects}) != len(objects):
        raise ValueError('Expected distinct named mesh objects')
    # The prepared scene owns assembly and pose. Bake the evaluated world geometry.
    depsgraph = bpy.context.evaluated_depsgraph_get()
    groups, points, triangles, omitted = {}, [], [], 0
    for spec in objects:
        obj = bpy.context.scene.objects.get(spec['name'])
        if obj is None or obj.type != 'MESH':
            raise ValueError('Missing mesh object: ' + spec['name'])
        evaluated = obj.evaluated_get(depsgraph)
        mesh = evaluated.to_mesh()
        try:
            mesh.calc_loop_triangles()
            if mesh.uv_layers.active is None:
                raise ValueError('Missing active UV layer: ' + obj.name)
            matrix = evaluated.matrix_world
            if abs(matrix.determinant()) < 1e-12:
                raise ValueError('Singular transform: ' + obj.name)
            normal_matrix = matrix.to_3x3().inverted().transposed()
            for tri in mesh.loop_triangles:
                key = str(tri.material_index)
                if key not in spec['materials']:
                    raise ValueError('Unmapped material slot: ' + obj.name + ':' + key)
                material = spec['materials'][key]
                if material is None:
                    omitted += 1
                    continue
                resource, cutout = material['texture'], material['cutout']
                if not isinstance(cutout, bool):
                    raise ValueError('cutout must be a JSON boolean')
                group = groups.setdefault((resource, cutout), [])
                loops = list(tri.loops)
                if matrix.determinant() < 0:
                    loops.reverse()
                face = []
                for loop in loops:
                    p = matrix @ mesh.vertices[mesh.loops[loop].vertex_index].co
                    n = (normal_matrix @ mesh.corner_normals[loop].vector).normalized()
                    uv = mesh.uv_layers.active.data[loop].uv
                    face.append((p.copy(), uv.copy(), n.copy()))
                    points.append(p.copy())
                triangles.append((group, face))
        finally:
            evaluated.to_mesh_clear()
    if not points:
        raise ValueError('No retained geometry')
    low, high = min(p.z for p in points), max(p.z for p in points)
    if high - low <= 1e-9:
        raise ValueError('Retained geometry has no Z height')
    scale = height / (high - low)
    for group, face in triangles:
        for p, uv, n in face:
            group.append((p.x*scale, (p.z-low)*scale, -p.y*scale, uv.x, 1-uv.y, n.x, n.z, -n.y))
    parts = [dict(texture=texture, cutout=cutout, vertices=vertices) for (texture, cutout), vertices in groups.items()]
    write_mesh(output / 'sample.mesh', parts)
    xyz = [vertex[:3] for part in parts for vertex in part['vertices']]
    scene = Path(bpy.data.filepath)
    report = {'status': 'EXPORTED', 'blender': bpy.app.version_string,
              'sceneSha256': hashlib.sha256(scene.read_bytes()).hexdigest() if scene.is_file() else None,
              'configSha256': hashlib.sha256(config_path.read_bytes()).hexdigest(),
              'triangles': len(triangles), 'omittedTriangles': omitted, 'heightBlocks': height,
              'bounds': {'min': [min(v[i] for v in xyz) for i in range(3)], 'max': [max(v[i] for v in xyz) for i in range(3)]},
              'groups': [{'texture': p['texture'], 'cutout': p['cutout'], 'triangles': len(p['vertices'])//3} for p in parts],
              'limits': 'Evaluated static pose; PNG textures supplied separately; no animation or PBR conversion'}
    (output / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    args = sys.argv[sys.argv.index('--')+1:]
    if args == ['inspect']:
        print(json.dumps(inspect_scene(), indent=2))
    elif len(args) == 3 and args[0] == 'export':
        export_scene(args[1], args[2])
    else:
        raise ValueError('Expected inspect OR export CONFIG NEW_OUTPUT')

