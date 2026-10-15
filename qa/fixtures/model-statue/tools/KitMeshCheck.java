import com.example.staticmodel.MeshData;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

// Plain Java agreement check; never copied into recipient production sources.
public final class KitMeshCheck {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        MeshData sample;
        try(InputStream in=Files.newInputStream(Paths.get(args[0]))){sample=MeshData.read(in);}
        require(sample.triangles==6 && sample.parts.size()==1,"Original sample counts");
        require(sample.parts.get(0).texture.equals("staticmodel:textures/statue/checker.png"),"Original sample resource");
        BufferedImage image=ImageIO.read(new File(args[1]));
        require(image!=null && image.getWidth()==16 && image.getHeight()==16,"Sample PNG decode");
        MeshData exported;
        try(InputStream in=Files.newInputStream(Paths.get(args[2]))){exported=MeshData.read(in);}
        require(exported.triangles==2 && exported.parts.size()==2,"Prepared scene counts");
        require(!exported.parts.get(0).cutout && exported.parts.get(1).cutout,"Cutout groups");
        for(MeshData.Part part:exported.parts){
            require(part.texture.startsWith("another_mod:textures/nested/"),"Generic namespace/nested path");
            float[] v=part.vertices;
            for(int i=0;i<v.length;i+=8){
                require(v[i+1]>=0 && v[i+1]<=3,"Normalized height");
                double norm=v[i+5]*v[i+5]+v[i+6]*v[i+6]+v[i+7]*v[i+7];
                require(Math.abs(norm-1)<.00001,"Transformed unit normals");
            }
            // Mirror correction preserves the direction of transformed corner normals.
            double ax=v[8]-v[0],ay=v[9]-v[1],az=v[10]-v[2];
            double bx=v[16]-v[0],by=v[17]-v[1],bz=v[18]-v[2];
            double dot=(ay*bz-az*by)*v[5]+(az*bx-ax*bz)*v[6]+(ax*by-ay*bx)*v[7];
            require(dot>0,"Winding agrees with transformed normals");
            require(v[3]==0 && v[4]==0 && v[11]==1 && v[12]==1 && v[19]==0 && v[20]==1,"Single UV V flip and mirrored loop order");
        }
        System.out.println("KIT_MESH_PASS originalTriangles=6 sceneTriangles=2 groups=2 PNG=true UV=true normals=true genericPaths=true");
    }
}
