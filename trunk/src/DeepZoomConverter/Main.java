package DeepZoomConverter;

/**
 *  Deep Zoom Converter
 *
 *  A Java application for converting large image files into the tiled images
 *  required by the Microsoft Deep Zoom format and is suitable for use with
 *  Daniel Gasienica's OpenZoom library presently at http://openzoom.org
 *
 *  This source code is provided in the form of a Java application, but is
 *  designed to be adapted for use in a library.
 *
 *  To simplify compliation, deployment and packaging I have provided the
 *  original code in a single source file.
 *
 *  Version: MPL 1.1/GPL 3/LGPL 3
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http: *www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is this Java package called DeepZoomConverter
 *
 *  The Initial Developer of the Original Code is Glenn Lawrence.
 *  Portions created by the Initial Developer are Copyright (c) 2007-2009
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *    Glenn Lawrence  <glenn.c.lawrence@gmail.com>
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 3 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 3 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;
import java.util.Vector;
import java.util.Iterator;

/**
 *
 * @author Glenn Lawrence
 */
public class Main {

    static final String xmlHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
    static final String schemaName = "http://schemas.microsoft.com/deepzoom/2009";

    private enum CmdParseState { DEFAULT, OUTPUTDIR, TILESIZE, OVERLAP, INPUTFILE };
    static Boolean deleteExisting = true;
    static String tileFormat = "jpg";

    // The following can be overriden/set by the indicated command line arguments
    static int tileSize = 256;            // -tilesize
    static int tileOverlap = 1;           // -overlap
    static File outputDir = null;         // -outputdir or -o
    static Boolean verboseMode = false;   // -verbose or -v
    static Boolean debugMode = false;     // -debug
    static Vector<File> inputFiles = new Vector();  // must follow all other args

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
      
        try {
            try {
                parseCommandLine(args);
                if (outputDir == null)
                    outputDir = new File(".");
                if (debugMode) {
                    System.out.printf("tileSize=%d ", tileSize);
                    System.out.printf("tileOverlap=%d ", tileOverlap);
                    System.out.printf("outputDir=%s\n", outputDir.getPath());
                }

            } catch (Exception e) {
                System.out.println("Invalid command line: " + e.getMessage());
                return;
            }

            if (!outputDir.exists())
                throw new FileNotFoundException("Output directory does not exist: "
                                                + outputDir.getPath());
            if (!outputDir.isDirectory())
                throw new FileNotFoundException("Output directory is not a directory: "
                                                + outputDir.getPath());

            Iterator<File> itr = inputFiles.iterator();
            while (itr.hasNext())
                 processImageFile(itr.next(), outputDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Process the command line arguments
     * @param args the command line arguments
     */
    private static void parseCommandLine(String[] args) throws Exception {
        CmdParseState state = CmdParseState.DEFAULT;
        for (int count = 0; count < args.length; count++) {
            String arg = args[count];
            switch (state) {
              case DEFAULT:
                  if (arg.equals("-verbose") || arg.equals("-v"))
                      verboseMode = true;
                  else if (arg.equals("-debug")) {
                      verboseMode = true;
                      debugMode = true;
                  }
                  else if (arg.equals("-outputdir") || arg.equals("-o"))
                      state = CmdParseState.OUTPUTDIR;
                  else if (arg.equals("-tilesize"))
                      state = CmdParseState.TILESIZE;
                  else if (arg.equals("-overlap"))
                      state = CmdParseState.OVERLAP;
                  else
                      state = CmdParseState.INPUTFILE;
                  break;
              case OUTPUTDIR:
                  outputDir = new File(arg);
                  state = CmdParseState.DEFAULT;
                  break;
              case TILESIZE:
                  tileSize = Integer.parseInt(arg);
                  state = CmdParseState.DEFAULT;
                  break;
              case OVERLAP:
                  tileOverlap = Integer.parseInt(arg);
                  state = CmdParseState.DEFAULT;
                  break;
            }
            if (state == CmdParseState.INPUTFILE) {
                File inputFile = new File(arg);
                if (!inputFile.exists())
                    throw new FileNotFoundException("Missing input file: " + inputFile.getPath());
                inputFiles.add(inputFile);
            }
        }
        if (inputFiles.size() == 0)
            throw new Exception("No input files given");
    }

    /**
     * Process the given image file, producing its Deep Zoom output files
     * in a subdirectory of the given output directory.
     * @param inFile the file containing the image
     * @param outputDir the output directory
     */
    private static void processImageFile(File inFile, File outputDir) throws IOException {
        if (verboseMode)
             System.out.printf("Processing image file: %s\n", inFile);

        String fileName = inFile.getName();
        String nameWithoutExtension = fileName.substring(0, fileName.lastIndexOf('.'));
        String pathWithoutExtension = outputDir + File.separator + nameWithoutExtension;

        BufferedImage image = loadImage(inFile);

        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();

        double maxDim = Math.max(originalWidth, originalHeight);

        int nLevels = (int)Math.ceil(Math.log(maxDim) / Math.log(2));

        if (debugMode)
            System.out.printf("nLevels=%d\n", nLevels);

        // Delete any existing output files and folders for this image

        File descriptor = new File(pathWithoutExtension + ".xml");
        if (descriptor.exists()) {
            if (deleteExisting)
                deleteFile(descriptor);
            else
                throw new IOException("File already exists in output dir: " + descriptor);
        }

        File imgDir = new File(pathWithoutExtension);
        if (imgDir.exists()) {
            if (deleteExisting) {
                if (debugMode)
                    System.out.printf("Deleting directory: %s\n", imgDir);
                deleteDir(imgDir);
            } else
                throw new IOException("Image directory already exists in output dir: " + imgDir);
        }

        imgDir = createDir(outputDir, nameWithoutExtension);

        double width = originalWidth;
        double height = originalHeight;

        for (int level = nLevels; level >= 0; level--) {
            int nCols = (int)Math.ceil(width / tileSize);
            int nRows = (int)Math.ceil(height / tileSize);
            if (debugMode)
                System.out.printf("level=%d w/h=%f/%f cols/rows=%d/%d\n",
                                   level, width, height, nCols, nRows);
            
            File dir = createDir(imgDir, Integer.toString(level));
            for (int col = 0; col < nCols; col++) {
                for (int row = 0; row < nRows; row++) {
                    BufferedImage tile = getTile(image, row, col);
                    saveImage(tile, dir + File.separator + col + '_' + row);
                }
            }

            // Scale down image for next level
            width = Math.ceil(width / 2);
            height = Math.ceil(height / 2);
            if (width > 10 && height > 10) {
                // resize in stages to improve quality
                image = resizeImage(image, width * 1.66, height * 1.66);
                image = resizeImage(image, width * 1.33, height * 1.33);
            }
            image = resizeImage(image, width, height);
        }

        saveImageDescriptor(originalWidth, originalHeight, descriptor);
    }


    /**
     * Delete a file
     * @param path the path of the directory to be deleted
     */
    private static void deleteFile(File file) throws IOException {
         if (!file.delete())
             throw new IOException("Failed to delete file: " + file);
    }

    /**
     * Recursively deletes a directory
     * @param path the path of the directory to be deleted
     */
    private static void deleteDir(File dir) throws IOException {
        if (!dir.isDirectory())
            deleteFile(dir);
        else {
            for (File file : dir.listFiles()) {
               if (file.isDirectory())
                   deleteDir(file);
               else
                   deleteFile(file);
            }
            if (!dir.delete())
                throw new IOException("Failed to delete directory: " + dir);
        }
    }

    /**
     * Creates a directory
     * @param parent the parent directory for the new directory
     * @param name the new directory name
     */
    private static File createDir(File parent, String name) throws IOException {
        assert(parent.isDirectory());
        File result = new File(parent + File.separator + name);
        if (!result.mkdir())
           throw new IOException("Unable to create directory: " + result);
        return result;
    }

    /**
     * Loads image from file
     * @param file the file containing the image
     */
    private static BufferedImage loadImage(File file) throws IOException {
        BufferedImage result = null;
        try {
            result = ImageIO.read(file);
        } catch (Exception e) {
            throw new IOException("Cannot read image file: " + file);
        }
        return result;
    }

    /**
     * Gets an image containing the tile at the given row and column
     * for the given image.
     * @param img - the input image from whihc the tile is taken
     * @param row - the tile's row (i.e. y) index
     * @param col - the tile's column (i.e. x) index
     */
    private static BufferedImage getTile(BufferedImage img, int row, int col) {
        int x = col * tileSize - (col == 0 ? 0 : tileOverlap);
        int y = row * tileSize - (row == 0 ? 0 : tileOverlap);
        int w = tileSize + (col == 0 ? 1 : 2) * tileOverlap;
        int h = tileSize + (row == 0 ? 1 : 2) * tileOverlap;

        if (x + w > img.getWidth())
            w = img.getWidth() - x;
        if (y + h > img.getHeight())
            h = img.getHeight() - y;

        if (debugMode)
            System.out.printf("getTile: row=%d, col=%d, x=%d, y=%d, w=%d, h=%d\n",
                              row, col, x, y, w, h);
        
        assert(w > 0);
        assert(h > 0);

        BufferedImage result = new BufferedImage(w, h, img.getType());
        Graphics2D g = result.createGraphics();
        g.drawImage(img, 0, 0, w, h, x, y, x+w, y+h, null);

        return result;
    }

    /**
     * Returns resized image
     * NB - useful reference on high quality image resizing can be found here:
     *   http://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html
     * @param width the required width
     * @param height the frequired height
     * @param img the image to be resized
     */
    private static BufferedImage resizeImage(BufferedImage img, double width, double height) {
        int w = (int)width;
        int h = (int)height;
        BufferedImage result = new BufferedImage(w, h, img.getType());
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, w, h, 0, 0, img.getWidth(), img.getHeight(), null);
        return result;
    }

    /**
     * Saves image to the given file
     * @param img the image to be saved
     * @param path the path of the file to which it is saved (less the extension)
     */
    private static void saveImage(BufferedImage img, String path) throws IOException {
        File outputFile = new File(path + "." + tileFormat);
        try {
            ImageIO.write(img, tileFormat, outputFile);
        } catch (IOException e) {
            throw new IOException("Unable to save image file: " + outputFile);
        }
    }

    /**
     * Write image descriptor XML file
     * @param width image width
     * @param height image height
     * @param file the file to which it is saved
     */
    private static void saveImageDescriptor(int width, int height, File file) throws IOException {
        Vector lines = new Vector();
        lines.add(xmlHeader);
        lines.add("<Image TileSize=\"" + tileSize + "\" Overlap=\"" + tileOverlap +
                  "\" Format=\"" + tileFormat + "\" ServerFormat=\"Default\" xmnls=\"" +
                  schemaName + "\">");
        lines.add("<Size Width=\"" + width + "\" Height=\"" + height + "\" />");
        lines.add("</Image>");
        saveText(lines, file);
    }

    /**
     * Saves strings as text to the given file
     * @param lines the image to be saved
     * @param file the file to which it is saved
     */
    private static void saveText(Vector lines, File file) throws IOException {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            PrintStream ps = new PrintStream(fos);
            for (int i = 0; i < lines.size(); i++)
                ps.println((String)lines.elementAt(i));
        } catch (IOException e) {
            throw new IOException("Unable to write to text file: " + file);
        }
    }

}
