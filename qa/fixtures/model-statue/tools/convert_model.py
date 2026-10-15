"""Blender batch conversion of the supplied Bunny FBX bundle; originals stay intact.
Run blender -b --factory-startup --python convert_model.py -- INPUT_ROOT OUTPUT_ROOT
OUTPUT_ROOT is generated assets/modelstatue; retain report/preview outside production.
"""
import bpy, json, pathlib, struct, sys, hashlib
from mathutils import Vector

args=sys.argv[sys.argv.index('--')+1:]
root,out=(pathlib.Path(v).resolve() for v in args[:2]);out.mkdir(parents=True,exist_ok=True)
texdir=out/'textures/statue';texdir.mkdir(parents=True,exist_ok=True)
modeldir=out/'models/statue';modeldir.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
parts={};arms={};sources=[]
for part,rel in [('body','Body and Head/BODY/Body.fbx'),('face','Face/Face.fbx'),('head','Body and Head/HEAD/Head-Main.fbx')]:
    before=set(bpy.data.objects);path=root/rel
    bpy.ops.import_scene.fbx(filepath=str(path))
    added=set(bpy.data.objects)-before
    parts[part]=next(o for o in added if o.type=='MESH')
    arms[part]=next(o for o in added if o.type=='ARMATURE')
    sources.append({'path':rel,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
attach=(arms['body'].matrix_world@arms['body'].data.bones['Bn_Socket_Head'].matrix_local) @ (arms['head'].matrix_world@arms['head'].data.bones['Pt_Head'].matrix_local).inverted()
bodytex='Body and Head/BODY/Material/000/Texture/'
headtex='Body and Head/HEAD/Material/000/Texture/'
facetex='Face/Texture/'
# Explicit associations for this asset; this is not a general game-material converter.
mapping={
    ('body',0):('part_a',bodytex+'PC_004_A0101_PartA_C.png',False),
    ('body',1):('part_b',bodytex+'PC_004_A0101_PartB_C.png',False),
    ('body',2):('skin',bodytex+'PC_004_Body_000_C.png',False),
    ('body',3):('skin',bodytex+'PC_004_Body_000_C.png',False),
    ('head',0):('hair',None,False),
    ('head',1):('helmet',headtex+'PC_004_A0101_Head_000_C.png',False),
    ('head',2):('visor',None,False),
    ('face',0):('face',bodytex+'PC_004_C0101_Face_C.png',False),
    ('face',1):('sclera',facetex+'T_EyeScleraBaseColor_Example.png',False),
    ('face',2):('mouth',bodytex+'PC_004_C0101_Face_C.png',False),
    ('face',3):('iris',facetex+'T_EyeIrisBaseColor_Example.png',False),
    # The game-specific wet/cornea shader overlay is omitted for a static diffuse proof.
    ('face',4):None,
    ('face',5):('brows',facetex+'T_eyebrow_d.png',True),
    ('face',6):('lashes',facetex+'T_eyelash2_D.png',True),
}
textures={};materials={}
for spec in mapping.values():
    if spec is None:continue
    name,rel,cutout=spec
    if name in textures:continue
    if rel:
        image=bpy.data.images.load(str(root/rel),check_existing=True)
        if max(image.size)>1024:image.scale(1024,1024)
        if not cutout:
            pixels=list(image.pixels);pixels[3::4]=[1.0]*(len(pixels)//4);image.pixels=pixels
    else:
        image=bpy.data.images.new(name,width=4,height=4)
        color=(.045,.027,.02,1) if name=='hair' else (.035,.055,.085,1)
        image.pixels=list(color)*16
    image.filepath_raw=str(texdir/(name+'.png'));image.file_format='PNG';image.save()
    textures[name]=image
    mat=bpy.data.materials.new(name);mat.use_nodes=True
    shader=mat.node_tree.nodes.get('Principled BSDF');shader.inputs['Roughness'].default_value=.65
    node=mat.node_tree.nodes.new('ShaderNodeTexImage');node.image=image
    mat.node_tree.links.new(node.outputs['Color'],shader.inputs['Base Color'])
    if cutout:mat.node_tree.links.new(node.outputs['Alpha'],shader.inputs['Alpha'])
    materials[name]=mat

meshes={};allpoints=[]
for part,obj in parts.items():
    evaluated=obj.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh=evaluated.to_mesh();mesh.calc_loop_triangles()
    matrix=obj.matrix_world.copy()
    if part=='head':matrix=attach@matrix
    normal_matrix=matrix.to_3x3().inverted().transposed()
    points=[matrix@v.co for v in mesh.vertices]
    allpoints.extend(points)
    meshes[part]=(obj,evaluated,mesh,matrix,normal_matrix,points)
low=min(p.z for p in allpoints);high=max(p.z for p in allpoints);scale=3.0/(high-low)
groups={};skipped=0;preview=[]
for part,(obj,evaluated,mesh,matrix,normal_matrix,points) in meshes.items():
    uv=mesh.uv_layers.active.data
    newmesh=bpy.data.meshes.new(part+'_static');verts=[((v.x)*scale,(v.y)*scale,(v.z-low)*scale) for v in points]
    faces=[];face_specs=[];face_uvs=[]
    for tri in mesh.loop_triangles:
        spec=mapping[(part,tri.material_index)]
        if spec is None:skipped+=1;continue
        name,rel,cutout=spec;group=groups.setdefault(name,{'cutout':cutout,'vertices':[]})
        faces.append(tuple(tri.vertices));face_specs.append(name);face_uvs.append([tuple(uv[l].uv) for l in tri.loops])
        for l in tri.loops:
            v=points[mesh.loops[l].vertex_index];n=(normal_matrix@mesh.corner_normals[l].vector).normalized();u=uv[l].uv
            group['vertices'].append((v.x*scale,(v.z-low)*scale,-v.y*scale,u.x,1-u.y,n.x,n.z,-n.y))
    newmesh.from_pydata(verts,[],faces);newmesh.update()
    names=list(dict.fromkeys(face_specs))
    for name in names:newmesh.materials.append(materials[name])
    uvlayer=newmesh.uv_layers.new(name='UVMap')
    for i,p in enumerate(newmesh.polygons):
        p.material_index=names.index(face_specs[i]);p.use_smooth=True
        for j,l in enumerate(p.loop_indices):uvlayer.data[l].uv=face_uvs[i][j]
    static=bpy.data.objects.new(part+'_static',newmesh);bpy.context.collection.objects.link(static);preview.append(static)
    obj.hide_render=True;evaluated.to_mesh_clear()
for arm in arms.values():arm.hide_render=True

with (modeldir/'bunny.mesh').open('wb') as f:
    f.write(b'ST3D');f.write(struct.pack('>ii',1,len(groups)))
    for name,group in groups.items():
        path=('modelstatue:textures/statue/'+name+'.png').encode('ascii')
        f.write(struct.pack('>H',len(path)));f.write(path);f.write(struct.pack('>?i',group['cutout'],len(group['vertices'])))
        for values in group['vertices']:f.write(struct.pack('>8f',*values))

report={'status':'CONVERTED','sources':sources,'heightBlocks':3,'groups':{name:{'triangles':len(g['vertices'])//3,'cutout':g['cutout']} for name,g in groups.items()},'triangles':sum(len(g['vertices'])//3 for g in groups.values()),'omittedWetOverlayTriangles':skipped,'textureMap':{f'{p}:{i}':spec for (p,i),spec in mapping.items()},'simplifications':['Static imported reference pose','Diffuse textures only; no original game PBR/wet shader','Hair/visor use chosen solid colors','Textures capped at 1024; cutout alpha preserved only for lashes/brows'],'credits':'Model bundle uploader nekopixil; character from The First Descendant'}
(out/'conversion.json').write_text(json.dumps(report,indent=2))
# CPU render provides an assembly/material preview without modifying input assets.
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=24
scene.world.color=(.18,.18,.18)
scene.render.resolution_x=900;scene.render.resolution_y=1100;scene.render.resolution_percentage=100
for location,energy,size in [((2,-4,5),650,4),((-3,-1,3),350,3),((1,3,4),450,3)]:
    light=bpy.data.lights.new('studio','AREA');light.energy=energy;light.shape='DISK';light.size=size
    o=bpy.data.objects.new('studio',light);bpy.context.collection.objects.link(o);o.location=location;o.rotation_euler=(Vector((0,0,1.5))-o.location).to_track_quat('-Z','Y').to_euler()
camera=bpy.data.objects.new('camera',bpy.data.cameras.new('camera'));bpy.context.collection.objects.link(camera)
camera.location=(3,-6,2.6);camera.rotation_euler=(Vector((0,0,1.5))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=3.8;scene.camera=camera
scene.view_settings.view_transform='Standard';scene.render.filepath=str(out/'preview.png')
bpy.ops.wm.save_as_mainfile(filepath=str(out/'assembled.blend'))
bpy.ops.render.render(write_still=True)
print('MODEL_CONVERT_PASS triangles='+str(report['triangles'])+' groups='+str(len(groups)))
