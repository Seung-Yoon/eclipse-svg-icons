import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** */
    private ExecutorService execPool;

    /** */
	private int threads;
	
	/** */
	private AtomicInteger counter;
    
    /**
     * 
     */
    public RasterizerUtil(int threads) {
        sourceDirs = new ArrayList<IconDef>();
        this.threads = threads;
        execPool = Executors.newFixedThreadPool(threads);
        counter = new AtomicInteger();
    }

    public int getIconsRendered() {
    	return counter.get();
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
     * @param dir
     */
    public void rasterize(IconDef dir) {
    	if(!dir.outputPath.exists()) {
    		dir.outputPath.mkdirs();
    	}
    	 
    	int[] sizes = dir.sizes;

        GrayscaleFilter grayFilter = new GrayscaleFilter();

        HSBAdjustFilter desaturator = new HSBAdjustFilter();
        desaturator.setBFactor(0.3f);

        ResampleOp resampleOp = new ResampleOp(16, 16);
        resampleOp.setFilter(ResampleFilters
                .getLanczos3Filter());
        // resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
        resampleOp.setNumberOfThreads(Runtime.getRuntime()
                .availableProcessors());

        for (int size : sizes) {
            File outputFile = new File(dir.outputPath, dir.nameBase + "-" + size + ".png");
            
            // Render to SVG
            // TODO do this entirely in memory instead of writing to a file
            try {
                FileInputStream fileInputStream = new FileInputStream(dir.inputPath);
                FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                System.out.println(Thread.currentThread().getName() + " " + " Rasterizing: " + outputFile.getName()
                        + " at " + size + "x" + size);
                renderIcon(size, size, fileInputStream, fileOutputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                continue;
            }

            BufferedImage read = null;
			try {
				read = ImageIO.read(outputFile);
			} catch (IOException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
            
            // Post process the raster output
            try {
                BufferedImage desaturated = desaturator.filter(
                        grayFilter.filter(read, null), null);

                ImageIO.write(desaturated, PNG, new File(dir.outputPath,
                        dir.nameBase + size + "-grey.png"));

            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            try {
                // Icons lose definition when rendered direct to 16x16 with
                // Batik
                // Here we resize a 32x32 image down, which gives better
                // results
                if (size == 32) {
                    BufferedImage rescaled = resampleOp.filter(read, null);

                    ImageIO.write(rescaled, PNG, new File(dir.outputPath,
                            dir.nameBase + ".png"));

                    BufferedImage desaturated16 = desaturator.filter(
                            grayFilter.filter(rescaled, null), null);

                    ImageIO.write(desaturated16, PNG,
                            new File(dir.outputPath, dir.nameBase
                                    + "-grey.png"));
                }
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }
    
    /**
     * 
     */
    public void rasterizeAll() {
    	int totalSource = sourceDirs.size();
    	final int execSize = sourceDirs.size() / this.threads;
    	
    	int start = 0;
    	 
    	List<Callable<Object>> tasks = new ArrayList<Callable<Object>>(this.threads);
    	
    	while(totalSource > 0) {
    		final int curStart = start;
        	start += execSize;
        	
        	int batchSize = 0;
        	
        	if(totalSource >= execSize) {
        		batchSize = execSize;
        	} else {
        		batchSize = totalSource;
        	}
        	
    		totalSource -= execSize;
        	
    		final int execCount = batchSize;
    		
        	Callable<Object> runnable = new Callable<Object>() {

				public Object call() throws Exception {
					for(int count=0; count<execCount; count++) {
    					rasterize(sourceDirs.get(curStart+count));
    				}
    				
    	        	counter.set(counter.get() + execCount);
    	        	System.out.println("Finished rendering batch, index: " + curStart);
    	        	
					return null;
				}
			};
        	
        	tasks.add(runnable);
    	}
    	
    	try {
    		execPool.invokeAll(tasks);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * 
     * @param width
     * @param height
     * @param input
     * @param stream
     */
    public static void renderIcon(int width, int height, InputStream input,
            OutputStream stream) {
        PNGTranscoder t = new PNGTranscoder();
        t.addTranscodingHint(PNGTranscoder.KEY_WIDTH, new Float(width));
        t.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, new Float(height));

        t.setErrorHandler(new ErrorHandler() {
            public void warning(TranscoderException arg0)
                    throws TranscoderException {
                System.err.println("WARN: " + arg0.getMessage());
            }

            public void fatalError(TranscoderException arg0)
                    throws TranscoderException {
                System.err.println("FATAL: " + arg0.getMessage());
            }

            public void error(TranscoderException arg0)
                    throws TranscoderException {
                System.err.println("ERROR: " + arg0.getMessage());
            }
        });

        TranscoderInput tinput = new TranscoderInput(input);
        TranscoderOutput output = new TranscoderOutput(stream);

        try {
            t.transcode(tinput, output);

            stream.close();
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }

    /**
     *  
     * @param raster
     * @param outputName
     * @param iconDir
     * @param outputBase
     * @param outputDir2 
     */
    public static void gatherIcons(RasterizerUtil raster, String outputName,
    		File rootDir, File iconDir, File outputBase) {
    	
        File[] listFiles = iconDir.listFiles();

        for (File child : listFiles) {
        	if(child.isDirectory()) {
        		gatherIcons(raster, outputName, rootDir, child, outputBase);
        		continue;
        	}
        	
        	if(child.getName().endsWith("svg")) {
        		// Compute a relative path for the output dir
                URI rootUri = rootDir.toURI();
                URI iconUri = iconDir.toURI();

                String relativePath = rootUri.relativize(iconUri).getPath();
                File outputDir = new File(outputBase, relativePath);
                raster.createIcon(child, outputDir, SIZES);
        	}
        }
    }

    /**
     * 
     *  
     * @param args
     */
    public static void main(String[] args) {
    	int threads = Math.max(1,  Runtime.getRuntime().availableProcessors() / 2);
    	if(args.length >= 1) {
    		threads = Integer.parseInt(args[0]);
    	}
    	
        RasterizerUtil rasterizer = new RasterizerUtil(threads);

        File mavenTargetDir = new File("target/");
        File resources = new File("src/main/resources/");
        
        for(File file : resources.listFiles()) {
        	String dirName = file.getName();
        	File outputBase = new File(mavenTargetDir, dirName);

        	gatherIcons(rasterizer, dirName, file, file, outputBase);
        }
        
        System.out.println("Rendering icons with " + threads + " threads.");
        long startTime = System.currentTimeMillis();
        rasterizer.rasterizeAll();
        
        // Account for each output size and the gray icons
        int fullIconCount = rasterizer.getIconsRendered() * ((SIZES.length + 1) * 2);
        System.out.println(fullIconCount + " Icons Rendered, Took: " + (System.currentTimeMillis()-startTime) + " ms.");
        
        System.exit(0);
    }

}