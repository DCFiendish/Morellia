import org.pepsoft.worldpainter.layers.Bo2Layer;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BoostLayerDensity {
    public static void main(String[] args) throws Exception {
        File inFile = new File(args[0]);
        File outFile = new File(args[1]);
        int newDensity = Integer.parseInt(args[2]);
        int newGridX = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        int newGridY = args.length > 4 ? Integer.parseInt(args[4]) : newGridX;

        Bo2Layer layer;
        try (ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(inFile)))) {
            layer = (Bo2Layer) ois.readObject();
        }

        System.out.println("Loaded '" + layer.getName() + "': density=" + layer.getDensity()
                + " gridX=" + layer.getGridX() + " gridY=" + layer.getGridY()
                + " randomDisplacement=" + layer.getRandomDisplacement());

        layer.setDensity(newDensity);
        layer.setGridX(newGridX);
        layer.setGridY(newGridY);

        System.out.println("Boosted to: density=" + layer.getDensity()
                + " gridX=" + layer.getGridX() + " gridY=" + layer.getGridY());

        try (ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(outFile)))) {
            oos.writeObject(layer);
        }
        System.out.println("Wrote " + outFile);
    }
}
