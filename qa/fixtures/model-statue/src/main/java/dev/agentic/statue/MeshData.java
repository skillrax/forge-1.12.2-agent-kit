package dev.agentic.statue;

import java.io.*;
import java.util.*;

// Plain bounded binary data reader; no client classes or OpenGL needed for asset checks.
public final class MeshData {
    public static final class Part {
        public final String texture;public final boolean cutout;public final float[] vertices;
        Part(String texture,boolean cutout,float[] vertices){this.texture=texture;this.cutout=cutout;this.vertices=vertices;}
    }
    public final List<Part> parts;
    public final int triangles;
    private MeshData(List<Part> parts,int triangles){this.parts=Collections.unmodifiableList(parts);this.triangles=triangles;}
    public static MeshData read(InputStream stream) throws IOException {
        if(stream==null)throw new FileNotFoundException("Statue mesh resource missing");
        DataInputStream in=new DataInputStream(new BufferedInputStream(stream));
        if(in.readInt()!=0x53543344 || in.readInt()!=1)throw new IOException("Unsupported statue mesh");
        int count=in.readInt();if(count<1 || count>32)throw new IOException("Material count out of range");
        List<Part> parts=new ArrayList<>();int total=0;
        for(int i=0;i<count;i++) {
            String texture=in.readUTF();
            if(!texture.matches("modelstatue:textures/statue/[a-z_]+\\.png"))throw new IOException("Invalid mesh texture path");
            boolean cutout=in.readBoolean();int vertices=in.readInt();
            if(vertices<3 || vertices%3!=0 || vertices>1500000 || total+vertices>1500000)throw new IOException("Vertex count out of range");
            total+=vertices;float[] data=new float[vertices*8];
            for(int j=0;j<data.length;j++){data[j]=in.readFloat();if(Float.isNaN(data[j]) || Float.isInfinite(data[j]))throw new IOException("Nonfinite vertex data");}
            parts.add(new Part(texture,cutout,data));
        }
        if(in.read()!=-1)throw new IOException("Unexpected mesh data after final material");
        return new MeshData(parts,total/3);
    }
}
