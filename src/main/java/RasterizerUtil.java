import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.jhlabs.image.GrayscaleFilter;
import com.jhlabs.image.HSBAdjustFilter;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

/**
 * <p>
 * A hastily written utility that rasterizes the Eclipse SVG icon set 
 * into raster PNG images that can be readily used in an Eclipse product.
 * </p>
 * 
 * @author Tony McCrary (tmccrary@l33tlabs.com)
 * 
 */
public class RasterizerUtil {

    private static final String PNG = "PNG";

    /**
     * <p>IconDef is a definition instance used to define an icon to rasterize,
     * where to put it and the dimensions to render it at.</p>
     */
    class IconDef {

        /** */
        String nameBase;

        /** */
        File inputPath;

        /** */
        int[] sizes;

        /** */
        File outputPath;

        /**
         * 
         * @param nameBase
         * @param inputPath
         * @param outputPath
         * @param sizes
         */
        public IconDef(String nameBase, File inputPath, File outputPath,
                int[] sizes) {
            this.nameBase = nameBase;
            this.sizes = sizes;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
        }
    }

    /** A list of directories with svg sources to rasterize. */
    private List<IconDef> sourceDirs;

    /** A list of the output render sizes. */
    private static final int[] SIZES = new int[] { 32, 64, 128, 256, 512, 1024 };

    /**
     * 
     */
    public RasterizerUtil() {
        sourceDirs = new ArrayList<IconDef>();
    }

    /**
     * 
     * @param input
     * @param outputDir
     * @param sizes
     */
    public void createIcon(File input, File outputDir, int[] sizes) {
        String name = input.getName();
        String[] split = name.split("\\.(?=[^\\.]+$)");

        sourceDirs.add(new IconDef(split[0], input, outputDir, sizes));
    }

    /**
     * 
     */
    public void rasterize() {
        for (IconDef dir : sourceDirs) {
            int[] sizes = dir.sizes;

            for (int size : sizes) {

                // Render to SVG
                // TODO do this entirely in memory instead of writing to a file
                BufferedImage read;
                try {
                    FileInputStream fileInputStream = new FileInputStream(dir.inputPath);
                    read = renderIcon(size, size, fileInputStream);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    continue;
                } catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
                    continue;
				} catch (SVGException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
                    continue;
				}

                // Write the image and post process versions
                try {
                	// Write the base output file
                    File outputFile = new File(dir.outputPath, dir.nameBase + size
                            + ".png");

                    System.out.println("Rasterizing: " + outputFile.getName()
                            + " at " + size + "x" + size);
                    ImageIO.write(read, PNG, outputFile);
                    
                    // Write the desaturated output file
                    GrayscaleFilter grayFilter = new GrayscaleFilter();

                    HSBAdjustFilter desaturator = new HSBAdjustFilter();
                    desaturator.setBFactor(0.3f);

                    BufferedImage desaturated = desaturator.filter(
                            grayFilter.filter(read, null), null);

                    System.out.println("Rasterizing desaturated: "
                                    + outputFile.getName() + " at " + size
                                    + "x" + size);

                    ImageIO.write(desaturated, PNG, new File(dir.outputPath,
                            dir.nameBase + size + "-grey.png"));

                    // Icons lose definition when rendered direct to 16x16 with
                    // Batik
                    // Here we resize a 32x32 image down, which gives better
                    // results
                    if (size == 32) {
                        System.out.println("Rasterizing: " + outputFile.getName()
                                        + " at " + 16 + "x" + 16);

                        ResampleOp resampleOp = new ResampleOp(16, 16);
                        resampleOp.setFilter(ResampleFilters
                                .getLanczos3Filter());
                        // resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
                        resampleOp.setNumberOfThreads(Runtime.getRuntime()
                                .availableProcessors());

                        BufferedImage rescaled = resampleOp.filter(read, null);

                        ImageIO.write(rescaled, PNG, new File(dir.outputPath,
                                dir.nameBase + 16 + ".png"));

                        BufferedImage desaturated16 = desaturator.filter(
                                grayFilter.filter(rescaled, null), null);

                        System.out
                                .println("Rasterizing desaturated: "
                                        + outputFile.getName() + " at " + 16
                                        + "x" + 16);
                        ImageIO.write(desaturated16, PNG,
                                new File(dir.outputPath, dir.nameBase + 16
                                        + "-grey.png"));
                    }
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        }
    }

    /**
     * 
     * @param width
     * @param height
     * @param input
     * @param stream
     * @throws IOException 
     * @throws SVGException 
     */
    public static BufferedImage renderIcon(int width, int height, InputStream input) throws IOException, SVGException {
    	SVGUniverse univ = new SVGUniverse();
    	
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		URI loadSVG = univ.loadSVG(input, "theme");
		
	    SVGDiagram diagram = univ.getDiagram(loadSVG);
	    
	    final Rectangle2D.Double rect = new Rectangle2D.Double();
	    diagram.getViewRect(rect);
	    
	    AffineTransform scaleXform = new AffineTransform();
	    scaleXform.setToScale(width / rect.width, height / rect.height);
	    AffineTransform oldXform = g.getTransform();
	    g.transform(scaleXform);
	    
	    diagram.render(g);
		
		g.dispose();
	
        return image;
    }

    /**
     * 
     * @param raster
     * @param outputName
     * @param iconDir
     * @param outputDir
     */
    public static void walkIconDir(RasterizerUtil raster, String outputName,
            File iconDir, File outputDir) {
        File[] listFiles = iconDir.listFiles();

        for (File svgDir : listFiles) {
            createIcons(raster, svgDir, outputDir);
        }
    }

    /**
     * 
     * @param raster
     * @param svgDir
     * @param outputDir
     */
    private static void createIcons(RasterizerUtil raster, File svgDir,
            File outputDir) {

        File[] listFiles = svgDir.listFiles(new FileFilter() {
            public boolean accept(File arg0) {
                return arg0.getName().endsWith("svg");
            }
        });

        for (File svg : listFiles) {
            raster.createIcon(svg, outputDir, SIZES);
        }
    }

    /**
     * 
     * 
     * @param args
     */
    public static void main(String[] args) {
        RasterizerUtil raster = new RasterizerUtil();

        File mavenTargetDir = new File("target/");

        // Ant
        File antUi = new File(
                "src/main/resources/org.eclipse.ant.ui/icons/full/");

        File antUiOutput = new File(mavenTargetDir, "org.eclipse.ant.ui");
        antUiOutput.mkdirs();

        walkIconDir(raster, "org.eclipse.ant.ui", antUi, antUiOutput);

        // JDT
        File jdtUi = new File(
                "src/main/resources/org.eclipse.jdt.ui/icons/full/");

        File jdtUiOutput = new File(mavenTargetDir, "org.eclipse.jdt.ui");
        jdtUiOutput.mkdirs();

        walkIconDir(raster, "org.eclipse.jdt.ui", jdtUi, jdtUiOutput);

        // Core UI
        File coreUi = new File("src/main/resources/org.eclipse.ui/icons/full/");

        File coreUiOutput = new File(mavenTargetDir, "org.eclipse.ui");
        coreUiOutput.mkdirs();

        walkIconDir(raster, "org.eclipse.ui", coreUi, coreUiOutput);
        
        // Debug UI
        File debugUi = new File("src/main/resources/org.eclipse.debug.ui/full/");

        File debugUiOutput = new File(mavenTargetDir, "org.eclipse.debug.ui");
        debugUiOutput.mkdirs();

        walkIconDir(raster, "org.eclipse.debug.ui", debugUi, debugUiOutput);

        raster.rasterize();
    }

}
