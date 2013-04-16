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

import com.mortennobel.imagescaling.AdvancedResizeOp;
import com.mortennobel.imagescaling.ResampleOp;


public class RasterizerUtil {

	class IconDir {
		String nameBase;
		File inputPath;
		int[] sizes;
		File outputPath;
		
		public IconDir(String nameBase, File inputPath, File outputPath, int[] sizes) {
			this.nameBase = nameBase;
			this.sizes = sizes;
			this.inputPath = inputPath;
			this.outputPath = outputPath;
		}
	}
	
	private List<IconDir> sourceDirs;
	private static final int[] SIZES = new int[] { 32, 64, 128, 256, 512, 1024 };
	
	public RasterizerUtil() {
		sourceDirs = new ArrayList<IconDir>();
	}
	
	public void createIcon(File input, File output, int[] sizes) {
		String name = input.getName();
		System.out.println("NAME: " + name);
		String[] split = name.split("\\.(?=[^\\.]+$)");
		System.out.println(split.length);
		
		sourceDirs.add(new IconDir(split[0], input, output, sizes));
	}
	
	public void rasterize() {
		for(IconDir dir : sourceDirs) {
			int[] sizes = dir.sizes;
			
			for(int size : sizes) {
				File outputFile = new File(dir.outputPath, dir.nameBase + size + ".png");
				
				try {
					FileInputStream fileInputStream = new FileInputStream(dir.inputPath);
					FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
					System.out.println("Rasterizing: " + outputFile.getName() + " at " + size + "x" + size);
					rastersizeSVG(size, size, fileInputStream, fileOutputStream);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				
				if(size == 32) {
					try {
						System.out.println("Rasterizing: " + outputFile.getName() + " at " + 16 + "x" + 16);
						BufferedImage read = ImageIO.read(outputFile);
						
						ResampleOp resampleOp = new ResampleOp (16,16);
		                //resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.VerySharp);
		                BufferedImage rescaled = resampleOp.filter(read, null);
		                
		                ImageIO.write(rescaled, "PNG", 
		                                new File(dir.outputPath, dir.nameBase + 16 + ".png"));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	public static void rastersizeSVG(int width, int height, InputStream input, OutputStream stream) {
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
	
	public static void walkIconDir(RasterizerUtil raster, String outputName, File iconDir, File outputDir) {
		File[] listFiles = iconDir.listFiles();
		
		for(File svgDir : listFiles) {
			createIcons(raster, svgDir, outputDir);
		}
	}
	
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
	 * @param args
	 */
	public static void main(String[] args) {
		RasterizerUtil raster = new RasterizerUtil();
		
		File mavenTarget = new File("target/");

		File antUi = new File("src/main/resources/org.eclipse.ant.ui/icons/full/");
		
		File antUiOutput = new File(mavenTarget, "org.eclipse.ant.ui");
		antUiOutput.mkdirs();
		
		walkIconDir(raster, "org.eclipse.ant.ui", antUi, antUiOutput);
		
		raster.rasterize();
	}

}
