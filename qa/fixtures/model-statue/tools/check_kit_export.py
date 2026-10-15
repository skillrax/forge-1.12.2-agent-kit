"""Focused prepared-scene exporter checks on original geometry, not Bunny/gameplay.
Run with Blender 4.2 --python-exit-code 1 --python ... -- KIT_SCRIPTS NEW_OUTPUT.
"""
import importlib.util
import json
from pathlib import Path
import sys
import bpy

scripts, out = [Path(v).resolve() for v in sys.argv[sys.argv.index('--')+1:]]
out.mkdir(parents=True, exist_ok=False)
spec = importlib.util.spec_from_file_location('kit_exporter', scripts / 'export_blender.py')
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
mesh = bpy.data.meshes.new('OriginalTest')
# Two retained triangles plus one explicitly omitted overlay at a much higher Z.
mesh.from_pydata([(0,0,0),(1,0,0),(0,0,2),(0,1,0),(0,0,100)], [], [(0,1,2),(0,2,3),(1,3,4)])
mesh.update()
uv = mesh.uv_layers.new()
for face in mesh.polygons:
    for index, loop in enumerate(face.loop_indices):
        uv.data[loop].uv = [(0,0), (1,0), (0,1)][index]
for name in ['Diffuse', 'Cutout', 'Omitted']:
    mesh.materials.append(bpy.data.materials.new(name))
for index, polygon in enumerate(mesh.polygons):
    polygon.material_index = index
obj = bpy.data.objects.new('InspectedObject', mesh)
bpy.context.collection.objects.link(obj)
obj.location = (1, 2, 5)
obj.scale = (-2, 1, 2)  # Mirrored, nonuniform transformed geometry.
bpy.context.view_layer.update()
config = {'heightBlocks': 3, 'objects': [{'name': obj.name, 'materials': {
    '0': {'texture': 'another_mod:textures/nested/diffuse_1.png', 'cutout': False},
    '1': {'texture': 'another_mod:textures/nested/cutout_2.png', 'cutout': True}, '2': None}}]}
config_path = out/'scene.json'
config_path.write_text(json.dumps(config))
bpy.ops.wm.save_as_mainfile(filepath=str(out/'prepared.blend'))
inspection = exporter.inspect_scene()
assert inspection['meshes'][0]['object'] == obj.name
assert len(inspection['meshes'][0]['materials']) == 3
(out/'inspection.json').write_text(json.dumps(inspection, indent=2))
exporter.export_scene(config_path, out/'export')
report = json.loads((out/'export/report.json').read_text())
assert report['triangles'] == 2 and report['omittedTriangles'] == 1
assert report['bounds']['min'] == [-.75, 0, -2.25]
assert report['bounds']['max'] == [.75, 3, -1.5]
assert [g['cutout'] for g in report['groups']] == [False, True]
print('KIT_SCENE_EXPORT_PASS triangles=2 groups=2 mirrored=true omitted=1 height=3')
