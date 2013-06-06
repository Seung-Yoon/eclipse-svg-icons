import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.batik.transcoder.ErrorHandler;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import com.jhlabs.image.GrayscaleFilter;
import com.jhlabs.image.HSBAdjustFilter;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

/**
 * <p>Rasterized the Eclipse SVG icon set into raster PNG images
 * that can be readily used in an Eclipse product.</p>
 * 
 * @author Tony McCrary (tmccrary@l33tlabs.com)
 *
 */
public class RasterizerUtil {

	private static final String PNG = "PNG";

	/**
	 * <p>IconDef is a definition instance used to define an icon
	 * to rasterize, where to put it and the dimensions to render it at.</p>
	 * 
	 * @author tmccrary
	 *
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
		public IconDef(String nameBase, File inputPath, File outputPath, int[] sizes) {
			this.nameBase = nameBase;
			this.sizes = sizes;
			this.inputPath = inputPath;
			this.outputPath = outputPath;
		}
	}
	
	/** */
	private List<IconDef> sourceDirs;

	/** */
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
		for(IconDef dir : sourceDirs) {
			int[] sizes = dir.sizes;
			
			for(int size : sizes) {
				File outputFile = new File(dir.outputPath, dir.nameBase + size + ".png");
				
				try {
					FileInputStream fileInputStream = new FileInputStream(dir.inputPath);
					FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
					System.out.println("Rasterizing: " + outputFile.getName() + " at " + size + "x" + size);
					renderIcon(size, size, fileInputStream, fileOutputStream);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}

				try {
					BufferedImage read = ImageIO.read(outputFile);
					
					GrayscaleFilter grayFilter = new GrayscaleFilter();
					
					HSBAdjustFilter desaturator = new HSBAdjustFilter();
						desaturator.setBFactor(0.3f);
						
					BufferedImage desaturated = desaturator.filter(grayFilter.filter(read, null), null);

					System.out.println("Rasterizing desaturated: " + outputFile.getName() + " at " + size + "x" + size);
					
	                ImageIO.write(desaturated, PNG, 
                            new File(dir.outputPath, dir.nameBase + size + "-grey.png"));
					
					// Icons lose definition when rendered direct to 16x16 with Batik
					// Here we resize a 32x32 image down, which gives better results
					if(size == 32) {
							System.out.println("Rasterizing: " + outputFile.getName() + " at " + 16 + "x" + 16);
							
							ResampleOp resampleOp = new ResampleOp (16,16);
								resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
								//resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
								resampleOp.setNumberOfThreads(Runtime.getRuntime().availableProcessors());
								
							BufferedImage rescaled = resampleOp.filter(read, null);
			                
			                ImageIO.write(rescaled, PNG, 
			                                new File(dir.outputPath, dir.nameBase + 16 + ".png"));
		
							BufferedImage desaturated16 = desaturator.filter(grayFilter.filter(rescaled, null), null);

							System.out.println("Rasterizing desaturated: " + outputFile.getName() + " at " + 16 + "x" + 16);
			                ImageIO.write(desaturated16, PNG, 
			                		new File(dir.outputPath, dir.nameBase + 16 + "-grey.png"));
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
	 */
	public static void renderIcon(int width, int height, InputStream input, OutputStream stream) {
        PNGTranscoder t = new PNGTranscoder();
        	t.addTranscodingHint(PNGTranscoder.KEY_WIDTH, new Float(width));
        	t.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, new Float(height));
        	
	        t.setErrorHandler(new ErrorHandler() {
				public void warning(TranscoderException arg0) throws TranscoderException {
					System.out.println("WARN: " + arg0.getMessage());
				}
				
				public void fatalError(TranscoderException arg0) throws TranscoderException {
					System.out.println("FATAL: " + arg0.getMessage());
				}
				
				public void error(TranscoderException arg0) throws TranscoderException {
					System.out.println("ERROR: " + arg0.getMessage());
				}
			});

        TranscoderInput tinput = new TranscoderInput(input);
        TranscoderOutput output = new TranscoderOutput(stream);

        try {
        	t.transcode(tinput, output);
        	
	        stream.close();
	        input.close();
        } catch(Exception e) {
        	e.printStackTrace();
        } finally {
        }
	}
	
	/**
	 * 
	 * @param raster
	 * @param outputName
	 * @param iconDir
	 * @param outputDir
	 */
	public static void walkIconDir(RasterizerUtil raster, String outputName, File iconDir, File outputDir) {
		File[] listFiles = iconDir.listFiles();
		
		for(File svgDir : listFiles) {
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
		
		for(File svg : listFiles) {
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
		File antUi = new File("src/main/resources/org.eclipse.ant.ui/icons/full/");
		
		File antUiOutput = new File(mavenTargetDir, "org.eclipse.ant.ui");
			antUiOutput.mkdirs();
		
		walkIconDir(raster, "org.eclipse.ant.ui", antUi, antUiOutput);
		
		// JDT
		File jdtUi = new File("src/main/resources/org.eclipse.jdt.ui/icons/full/");
		
		File jdtUiOutput = new File(mavenTargetDir, "org.eclipse.jdt.ui");
		jdtUiOutput.mkdirs();
	
		walkIconDir(raster, "org.eclipse.jdt.ui", jdtUi, jdtUiOutput);


		// Core UI
		File coreUi = new File("src/main/resources/org.eclipse.ui/icons/full/");
		
		File coreUiOutput = new File(mavenTargetDir, "org.eclipse.ui");
		coreUiOutput.mkdirs();
	
		walkIconDir(raster, "org.eclipse.ui", coreUi, coreUiOutput);
		
		raster.rasterize();
	}

}
