import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectTube;
import org.pepsoft.worldpainter.layers.bo2.Schem;
import org.pepsoft.worldpainter.objects.WPObject;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class MakeLayer {
    public static void main(String[] args) throws Exception {
        String outName = args[0];
        String outFile = args[1];
        List<WPObject> objects = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            File f = new File(args[i]);
            Schem schem = Schem.load(f);
            schem.setName(f.getName().replaceFirst("\\.schem$", ""));
            objects.add(schem);
            System.out.println("Loaded " + schem.getName() + " dims=" + schem.getDimensions());
        }
        Bo2ObjectTube tube = new Bo2ObjectTube(outName, objects);
        Bo2Layer layer = new Bo2Layer(tube, outName, new Color(34, 139, 34));
        layer.setDensity(20);

        try (ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(outFile)))) {
            oos.writeObject(layer);
        }
        System.out.println("Wrote " + outFile);
    }
}
