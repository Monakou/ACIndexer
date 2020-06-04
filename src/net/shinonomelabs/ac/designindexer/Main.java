package net.shinonomelabs.ac.designindexer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Main {
    public static double colourDist(int c1, int c2) {
        int r1 = (c1 & 0xff0000) >> 16;
        int g1 = (c1 & 0x00ff00) >>  8;
        int b1 =  c1 & 0x0000ff       ;
        int r2 = (c2 & 0xff0000) >> 16;
        int g2 = (c2 & 0x00ff00) >>  8;
        int b2 =  c2 & 0x0000ff       ;

        return Math.sqrt((r1-r2)*(r1-r2) + (g1-g2)*(g1-g2) + (b1-b2)*(b1-b2));
    }

    public static void main(String[] args) {
        if(args.length < 1) {
            System.err.println("Usage: <image-path> [tile-width]");
            System.exit(1);
        }

        File imageFile = new File(args[0]);
        int tileWidth = 1;
        if(args.length >= 2) {
            tileWidth = Integer.parseInt(args[1]);
        }

        try {
            BufferedImage image = ImageIO.read(imageFile);
            int w = image.getWidth();
            int h = image.getHeight();

            // stage 1 - approximate image
            System.out.println("=== STAGE 1 ===");
            System.out.println("Determining optimal tile dimensions... (given width: " + tileWidth + ")");
            double ratio = (double) w/h;
            int tileHeight = 1;
            for(int i = 1; true; i++) {
                if (Math.abs(ratio - tileWidth / (double) i) < Math.abs(ratio - tileWidth / (double) (i + 1))) break;
                else tileHeight = i;
            }
            System.out.println("The optimal tile dimensions are " + tileWidth + "x" + tileHeight + ".");
            System.out.println("Scaling and cropping the image...");
            // crop first
            BufferedImage croppedImage;
            if(ratio > (float)tileWidth/tileHeight) { // too wide
                int wOptimal = (int)(h*tileWidth/tileHeight);
                System.out.println(w+"x"+h+" -> "+wOptimal+"x"+h);
                int excess = w - wOptimal;
                croppedImage = image.getSubimage(excess/2, 0, wOptimal, h);
            }
            else { // too tall
                int hOptimal = (int) (w * tileHeight / tileWidth);
                System.out.println(w + "x" + h + " -> " + w + "x" + hOptimal);
                int excess = h - hOptimal;
                croppedImage = image.getSubimage(0, excess / 2, w, hOptimal);
            }
            // scale image
            Image scaledImage = croppedImage.getScaledInstance(tileWidth * 32, tileHeight * 32, Image.SCALE_AREA_AVERAGING);
            BufferedImage scaledImageBuffered = new BufferedImage(tileWidth * 32, tileHeight * 32, BufferedImage.TYPE_INT_ARGB);
            Graphics sibg = scaledImageBuffered.createGraphics();
            sibg.drawImage(scaledImage, 0, 0, null);
            sibg.dispose();

            System.out.println("Approximating image to Animal Crossing palette...");
            w = tileWidth * 32;
            h = tileHeight * 32;
            int[] pixelData = new int[w * h];
            scaledImageBuffered.getRGB(0, 0, w, h, pixelData, 0, w);

            for(int y = 0; y < h; y++) {
                for(int x = 0; x < w; x++) {
                    int c = pixelData[y * w + x];
                    int r = (c & 0xff0000) >> 16;
                    int g = (c & 0x00ff00) >>  8;
                    int b =  c & 0x0000ff       ;

                    float[] hsb = new float[3];
                    Color.RGBtoHSB(r, g, b, hsb);
                    hsb[0] = (int)(hsb[0] * 30 + 0.5) / 30.0f;
                    hsb[1] = (int)(hsb[1] * 15 + 0.5) / 15.0f;
                    hsb[2] = (int)(hsb[2] * 15 + 0.5) / 15.0f;

                    pixelData[y * w + x] = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                }
            }

            System.out.println();
            System.out.println("=== STAGE 2 ===");
            System.out.println("Counting indexed colours...");
            Map<Integer, Integer> colourFrequency = new HashMap<>();
            for(int c : pixelData) {
                if(colourFrequency.containsKey(c)) {
                    colourFrequency.put(c, colourFrequency.get(c) + 1);
                }
                else {
                    colourFrequency.put(c, 1);
                }
            }

            System.out.println("There are " + colourFrequency.size() + " colours.");
            if(colourFrequency.size() > 15) {
                System.out.println("Reduction required. Performing reduction...");
                int r = 1;
                while(colourFrequency.size() > 15) {
                    //System.out.println("Performing reduction, radius " + r + ".");
                    Map<Integer,Integer> colourFrequencySorted = new LinkedHashMap<>();
                    colourFrequency.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                            .forEachOrdered(pair -> colourFrequencySorted.put(pair.getKey(), pair.getValue()));

                    List<Integer> assimilatedColours = new ArrayList<>();

                    for(int c1 : colourFrequencySorted.keySet()) {
                        if(assimilatedColours.contains(c1)) continue;
                        for(int c2 : colourFrequencySorted.keySet()) {
                            if(assimilatedColours.contains(c2)) continue;
                            if(c1 == c2) continue;
                            if(colourDist(c1, c2) <= r) {
                                //System.out.println(String.format("%06X assimilates %06X, radius %.3f.", c1, c2, colourDist(c1, c2)));
                                for(int y = 0; y < h; y++) {
                                    for(int x = 0; x < w; x++) {
                                        if(pixelData[y*w + x] == c2) {
                                            pixelData[y*w + x] = c1;
                                        }
                                    }
                                }
                                colourFrequency.put(c1, colourFrequency.get(c1) + colourFrequency.get(c2));
                                colourFrequency.remove(c2);
                                assimilatedColours.add(c2);
                            }
                        }
                    }
                    r++;
                }
            }

            System.out.println("Reduction finished.");
            BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            result.setRGB(0, 0, w, h, pixelData, 0, w);

            ImageIO.write(result, "png", new File("/home/liz/Pictures/result.png"));

            System.out.println("Image written to file.");
            System.out.println("The colours are:");
            Map<Integer,Character> colourMap = new LinkedHashMap<>();
            char l = '0';
            for(int c : colourFrequency.keySet()) {
                float[] hsb = new float[3];
                Color.RGBtoHSB((c & 0xff0000) >> 16, (c & 0x00ff00) >> 8, c & 0x0000ff, hsb);
                int hue = (int)(hsb[0]*30)+1;
                int sat = (int)(hsb[1]*15)+1;
                int val = (int)(hsb[2]*15)+1;
                colourMap.put(c, l);
                System.out.println(String.format("%c\t(%d,%d,%d)", l, hue, sat, val));
                l++;
                if(l == ':') l = 'a';
            }

            System.out.println("The map is:");
            for(int y = 0; y < tileHeight * 32; y++) {
                for(int x = 0; x < tileWidth * 32; x++) {
                    int c = pixelData[y*w + x];
                    System.out.print(colourMap.get(c));
                    System.out.print(' ');
                }
                System.out.println();
            }
        } catch(IOException ex) {
            // TODO handle
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
