import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.transcoder.ErrorHandler;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;

import com.jhlabs.image.GrayscaleFilter;
import com.jhlabs.image.HSBAdjustFilter;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

/**
 * <p>A hastily written utility that rasterizes the Eclipse SVG icon set into
 * raster PNG images that can be readily used in an Eclipse product.</p>
 * 
 * @author Tony McCrary (tmccrary@l33tlabs.com)
 * 
 */
public class RasterizerUtil {

	private static final String PNG = "PNG";

	/**
	 * <p>IconDef is used to define an icon to rasterize,
	 * where to put it and the dimensions to render it at.</p>
	 */
	class IconDef {

		/** The name of the icon minus extension */
		String nameBase;

		/** The input path of the source svg files. */
		File inputPath;

		/** The sizes this icon should be rendered at */
		int[] sizes;

		/**
		 * The path rasterized versions of this icon should be written into.
		 */
		File outputPath;

		/** The path to the 128x128 raster variant of this icon. */
		File galleryRasterPath;

		/** The path to a disabled version of the icon (gets desaturated). */
		private File disabledPath;

		/**
		 * 
		 * @param nameBase
		 * @param inputPath
		 * @param outputPath
		 * @param disabledPath
		 * @param sizes
		 */
		public IconDef(String nameBase, File inputPath, File outputPath,
				File disabledPath, int[] sizes) {
			this.nameBase = nameBase;
			this.sizes = sizes;
			this.inputPath = inputPath;
			this.outputPath = outputPath;
			this.disabledPath = disabledPath;
		}
	}

	/** A list of directories with svg sources to rasterize. */
	private List<IconDef> icons;

	/** A list of the output render sizes. */
	private static final int[] SIZES = new int[] { 128 };

	/** The pool used to render multiple icons concurrently. */
	private ExecutorService execPool;

	/** The number of threads to use when rendering icons. */
	private int threads;

	/**
	 * A counter used to keep track of the number of rendered icons. Atomic is
	 * used to make it easy to access between threads concurrently.
	 */
	private AtomicInteger counter;

	/**
	 * A collection of lists for each Eclipse icon sets (o.e.workbench.ui,
	 * o.e.jd.ui, etc).
	 */
	Map<String, List<IconDef>> galleryIconSets;

	/** */
	List<IconDef> failedIcons = Collections
			.synchronizedList(new ArrayList<IconDef>(5));

	/**
	 * @param threads
	 *            the number of threads to use when rendering icons
	 */
	public RasterizerUtil(int threads) {
		icons = new ArrayList<IconDef>();
		this.threads = threads;
		execPool = Executors.newFixedThreadPool(threads);
		counter = new AtomicInteger();

		galleryIconSets = new HashMap<String, List<IconDef>>();
	}

	/**
	 * 
	 * @return the number of icons rendered at the time of the call.
	 */
	public int getIconsRendered() {
		return counter.get();
	}

	/**
	 * Creates an IconDef during the icon gather operation.
	 * 
	 * @param input
	 * @param outputPath
	 * @param sizes
	 * @return
	 */
	public IconDef createIcon(File input, File outputPath, File disabledPath,
			int[] sizes) {
		String name = input.getName();
		String[] split = name.split("\\.(?=[^\\.]+$)");

		IconDef def = new IconDef(split[0], input, outputPath, disabledPath,
				sizes);

		icons.add(def);

		return def;
	}

	/**
	 * Generates a set of raster images from the input SVG vector image.
	 * TODO break this up
	 * 
	 * @param icon
	 *            the icon to render
	 */
	public void rasterize(IconDef icon) {
		if (icon == null) {
			System.err.println("Null icon definition, skipping.");
		}

		if (icon.inputPath == null) {
			System.err.println("Null icon input path, skipping: "
					+ icon.nameBase);
		}

		if (!icon.inputPath.exists()) {
			System.err.println("Input path specified does not exist, skipping: "
							+ icon.nameBase);
		}

		if (icon.outputPath != null && !icon.outputPath.exists()) {
			icon.outputPath.mkdirs();
		}

		if (icon.disabledPath != null && !icon.disabledPath.exists()) {
			icon.disabledPath.mkdirs();
		}

		int[] sizes = icon.sizes;

		GrayscaleFilter grayFilter = new GrayscaleFilter();

		HSBAdjustFilter desaturator = new HSBAdjustFilter();
		desaturator.setSFactor(0.0f);

		// Load the document and find out the native height/width
		// We reuse the document later for rasterization
		SVGDocument svgDocument = null;
		try {
			FileInputStream iconDocumentStream = new FileInputStream(
					icon.inputPath);

			String parser = XMLResourceDescriptor.getXMLParserClassName();
			SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
			svgDocument = (SVGDocument) f.createDocument(icon.nameBase,
					iconDocumentStream);
		} catch (Exception e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
			failedIcons.add(icon);
			return;
		}

		Element svgDocumentNode = svgDocument.getDocumentElement();
		String nativeWidthStr = svgDocumentNode.getAttribute("width");
		String nativeHeightStr = svgDocumentNode.getAttribute("width");

		int nativeWidth = Integer.parseInt(nativeWidthStr);
		int nativeHeight = Integer.parseInt(nativeHeightStr);

		int doubleWidth = nativeWidth * 2;
		int doubleHeight = nativeHeight * 2;

		int quadWidth = nativeWidth * 4;
		int quadHeight = nativeHeight * 4;

		// Can we just make these static field values?
		// Will that work with the graphics subsystem?
		ResampleOp resampleOpNative = new ResampleOp(nativeWidth, nativeHeight);
		resampleOpNative.setFilter(ResampleFilters.getLanczos3Filter());
		// resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
		resampleOpNative.setNumberOfThreads(2);

		ResampleOp resampleOpDouble = new ResampleOp(doubleWidth, doubleHeight);
		resampleOpDouble.setFilter(ResampleFilters.getLanczos3Filter());
		// resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
		resampleOpDouble.setNumberOfThreads(2);

		// Guesstimate the PNG size in memory, BAOS will enlarge if necessary.
		int outputInitSize = quadWidth * quadHeight * 4 + 1024;
		ByteArrayOutputStream iconOutput = new ByteArrayOutputStream(
				outputInitSize);

		// Render to SVG
		// TODO clean this up so it's less spaghetti-ish (use exceptions
		// properly, etc)
		try {
			System.out.println(Thread.currentThread().getName() + " "
					+ " Rasterizing: " + icon.nameBase + ".png at " + quadWidth
					+ "x" + quadHeight);
			boolean success = renderIcon(quadWidth, quadHeight,
					new TranscoderInput(svgDocument), iconOutput);
			if (!success) {
				System.out.println("Failed to render icon: " + icon.nameBase
						+ ".png, skipping.");
				failedIcons.add(icon);
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
			failedIcons.add(icon);
			return;
		}

		// Generate a buffered image from Batik's png output
		ByteArrayInputStream imageInputStream = new ByteArrayInputStream(
				iconOutput.toByteArray());
		BufferedImage read = null;
		try {
			read = ImageIO.read(imageInputStream);
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			failedIcons.add(icon);
			return;
		}

		// Post process the raster output
		// We need a grey version of each icon for disabled states
		try {
			ImageIO.write(read, PNG, new File(icon.outputPath, icon.nameBase
					+ "_4x.png"));

			if (icon.disabledPath != null) {
				BufferedImage desaturated = desaturator.filter(
						grayFilter.filter(read, null), null);

				ImageIO.write(desaturated, PNG, new File(icon.disabledPath,
						icon.nameBase + "_4x.png"));
			}

			// We use the 128 for various operations when rendering the icon
			// galleries
			icon.galleryRasterPath = new File(icon.outputPath, icon.nameBase
					+ "_4x.png");
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			failedIcons.add(icon);
			return;
		}

		try {
			// Icons lose definition and accuracy when rendered directly
			// to <128px res with Batik
			// Here we resize a 16x,32x,48x,64x images down, which gives better
			// results
			System.out.println(Thread.currentThread().getName() + " "
					+ " Rasterizing (Scaling Native): " + icon.nameBase
					+ ".png at " + nativeWidth + "x" + nativeHeight);

			// Resize and render the 16x16 icon
			BufferedImage rescaled16 = resampleOpNative.filter(read, null);

			ImageIO.write(rescaled16, PNG, new File(icon.outputPath,
					icon.nameBase + ".png"));

			if (icon.disabledPath != null) {
				BufferedImage desaturated16 = desaturator.filter(
						grayFilter.filter(rescaled16, null), null);

				ImageIO.write(desaturated16, PNG, new File(icon.disabledPath,
						icon.nameBase + ".png"));
			}

			System.out.println(Thread.currentThread().getName() + " "
					+ " Rasterizing (Scaling Half): " + icon.nameBase
					+ ".png at " + doubleWidth + "x" + doubleWidth);

			// Resize and render the 32x32 icon
			BufferedImage rescaled32 = resampleOpDouble.filter(read, null);

			ImageIO.write(rescaled32, PNG, new File(icon.outputPath,
					icon.nameBase + "_2x.png"));

			if (icon.disabledPath != null) {
				BufferedImage desaturated32 = desaturator.filter(
						grayFilter.filter(rescaled32, null), null);

				ImageIO.write(desaturated32, PNG, new File(icon.disabledPath,
						icon.nameBase + "_2x.png"));
			}
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			failedIcons.add(icon);
		}
	}

	/**
	 * Handles concurrently rasterizing the icons on many different threads to
	 * reduce the time duration on multicore systems.
	 */
	public void rasterizeAll() {
		int totalSourceIcons = icons.size();
		final int threadExecSize = icons.size() / this.threads;

		int start = 0;

		List<Callable<Object>> tasks = new ArrayList<Callable<Object>>(
				this.threads);

		// Distribute the rasterization operations between multiple threads
		while (totalSourceIcons > 0) {
			final int curStart = start;
			start += threadExecSize;

			int batchSize = 0;

			// Determine if we can fit a full batch in this callable
			// or if we are at the remainder end of gathered icons
			if (totalSourceIcons >= threadExecSize) {
				batchSize = threadExecSize;
			} else {
				batchSize = totalSourceIcons;
			}

			totalSourceIcons -= threadExecSize;

			final int execCount = batchSize;

			Callable<Object> runnable = new Callable<Object>() {
				public Object call() throws Exception {
					for (int count = 0; count < execCount; count++) {
						rasterize(icons.get(curStart + count));
					}

					counter.set(counter.get() + execCount);
					System.out.println("Finished rendering batch, index: "
							+ curStart);

					return null;
				}
			};

			tasks.add(runnable);
		}

		// Execute the rasterization operations that
		// have been added to the pool
		try {
			execPool.invokeAll(tasks);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Print info about failed render operations, so they can be fixed
		System.out.println("Failed Icon Count: " + failedIcons.size());
		for (IconDef icon : failedIcons) {
			System.out.println("Failed Icon: " + icon.nameBase);
		}

	}

	/**
	 * Use batik to rasterize the input SVG into a raster image at the specified
	 * image dimensions.
	 * 
	 * @param width
	 * @param height
	 * @param input
	 * @param stream
	 */
	public static boolean renderIcon(int width, int height,
			TranscoderInput tinput, OutputStream stream) {
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

		TranscoderOutput output = new TranscoderOutput(stream);

		try {
			t.transcode(tinput, output);

			stream.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {

		}
	}

	/**
	 * Search the resources directory for svg icons and add them to our
	 * collection for rasterization later.
	 * 
	 * @param raster
	 * @param outputName
	 * @param iconDir
	 * @param outputBase
	 * @param outputDir2
	 */
	public void gatherIcons(String outputName, File rootDir, File iconDir,
			File outputBase) {

		File[] listFiles = iconDir.listFiles();

		for (File child : listFiles) {
			if (child.isDirectory()) {
				gatherIcons(outputName, rootDir, child, outputBase);
				continue;
			}

			if (child.getName().endsWith("svg")) {
				// Compute a relative path for the output dir
				URI rootUri = rootDir.toURI();
				URI iconUri = iconDir.toURI();

				String relativePath = rootUri.relativize(iconUri).getPath();
				File outputDir = new File(outputBase, relativePath);
				File disabledOutputDir = null;

				File parentFile = child.getParentFile();

				// Determine if/where to put a disabled version of the icon
				// Eclipse traditionally uses a prefix of d for disabled, e for
				// enabled
				// in the folder name
				if (parentFile != null) {
					String parentDirName = parentFile.getName();
					if (parentDirName.startsWith("e")) {
						StringBuilder builder = new StringBuilder();
						builder.append("d");
						builder.append(parentDirName.substring(1,
								parentDirName.length()));
						String disabledVariant = builder.toString();

						File setParent = parentFile.getParentFile();
						for (File disabledFolder : setParent.listFiles()) {
							if (disabledFolder.getName()
									.equals(disabledVariant)) {
								String path = rootUri.relativize(
										disabledFolder.toURI()).getPath();
								disabledOutputDir = new File(outputBase, path);
							}
						}

					}
				}

				IconDef createIcon = createIcon(child, outputDir,
						disabledOutputDir, SIZES);

				// Update the gallery icons sets
				List<IconDef> list = galleryIconSets.get(rootDir.getName());

				if (list == null) {
					list = new ArrayList<IconDef>();
					galleryIconSets.put(rootDir.getName(), list);
				}

				list.add(createIcon);
			}
		}
	}

	/**
	 * Renders each icon set into a gallery image for reviewing and showing off
	 * icons, and then composes them into a master gallery image.
	 * 
	 * @param rasterizer
	 * @param targetDir
	 * @param iconSize
	 * @param width
	 */
	public void renderGalleries(File targetDir, int iconSize, int width) {
		// Render each icon set and a master list
		List<IconDef> masterList = new ArrayList<IconDef>();
		Map<String, List<IconDef>> iconSets2 = galleryIconSets;

		for (Entry<String, List<IconDef>> entry : iconSets2.entrySet()) {
			String key = entry.getKey();
			List<IconDef> value = entry.getValue();

			masterList.addAll(value);

			System.out.println("Creating gallery for: " + key);
			renderGallery(targetDir, key, value, iconSize, width, 3);
		}

		// Render the master image
		System.out.println("Rendering master icon gallery...");
		renderMasterGallery(targetDir, iconSize, iconSize + width, true);
		renderMasterGallery(targetDir, iconSize, iconSize + width, false);
	}

	/**
	 * Renders an icon set into a grid within an image.
	 * 
	 * @param root
	 * @param key
	 * @param value
	 */
	private void renderGallery(File root, String key, List<IconDef> value,
			int iconSize, int width, int margin) {
		int textHeaderHeight = 31;
		int outputSize = iconSize;
		int outputTotal = outputSize + (margin * 2);
		int div = width / outputTotal;
		int rowCount = value.size() / div;
		if (width % outputTotal > 0) {
			rowCount++;
		}

		// Compute the height and add some room for the text header (31 px)
		int height = Math.max(outputTotal, rowCount * outputTotal)
				+ textHeaderHeight;

		BufferedImage bi = new BufferedImage(width + iconSize, height,
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(Color.GRAY);
		g.drawString("SVG Icon Set: " + key + " - Count: " + value.size(), 8,
				20);

		int x = 1;
		int y = textHeaderHeight;

		// Render
		ResampleOp resampleOp = new ResampleOp(outputSize, outputSize);
		resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
		// resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
		resampleOp.setNumberOfThreads(Runtime.getRuntime()
				.availableProcessors());

		// Render each icon into the gallery grid
		for (IconDef def : value) {
			// Skip failed icons
			if (failedIcons.contains(def)) {
				continue;
			}

			try {
				if (def.galleryRasterPath == null) {
					System.err.println("Undefined gallery image for : "
							+ def.nameBase);
					continue;
				}
				BufferedImage iconImage = ImageIO.read(def.galleryRasterPath);
				BufferedImage sizedImage = resampleOp.filter(iconImage, null);

				g.drawImage(sizedImage, x + margin, y + margin, null);

				x += outputTotal;

				if (x >= width) {
					x = 1;
					y += outputTotal;
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.err.println("Error rendering icon for gallery: "
						+ def.galleryRasterPath.getAbsolutePath());
				continue;
			}
		}

		try {
			// Write the gallery image to disk
			ImageIO.write(bi, "PNG", new File(root, key + "-" + iconSize
					+ "-gallery.png"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Renders a master gallery image that contains every icon set at the
	 * current resolution.
	 * 
	 * @param root
	 * @param iconSize
	 * @param width
	 * @param dark
	 */
	private void renderMasterGallery(File root, int iconSize, int width,
			boolean dark) {
		int headerHeight = 30;
		List<BufferedImage> images = new ArrayList<BufferedImage>();
		for (File file : root.listFiles()) {
			if (file.getName().endsWith(iconSize + "-gallery.png")) {
				BufferedImage set = null;
				try {
					set = ImageIO.read(file);
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
				images.add(set);
				headerHeight += set.getHeight();
			}
		}

		BufferedImage bi = new BufferedImage(width, headerHeight,
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		if (dark) {
			g.setColor(Color.DARK_GRAY);
		} else {
			g.setColor(Color.WHITE);
		}
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());

		g.setColor(Color.BLACK);
		g.drawString("l33t labs Bling3D SVG Icons for Eclipse - Count: "
				+ counter.get() + " " + iconSize + "x" + iconSize
				+ " Rendered: " + new Date().toString(), 8, 20);

		int x = 0;
		int y = 31;

		// Draw each icon set image into the uber gallery
		for (BufferedImage image : images) {
			g.drawImage(image, x, y, null);
			y += image.getHeight();
		}

		try {
			// Write the uber gallery to disk
			String bgState = (dark) ? "dark" : "light";
			ImageIO.write(bi, "PNG", new File(root, "global-svg-" + iconSize
					+ "-" + bgState + "-icons.png"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Rasterize the project's icons and generate galleries for review
	 * 
	 * @param args
	 *            the first argument is an integer for the number of threads
	 *            used to render the project
	 */
	public static void main(String[] args) {
		int threads = Math.max(1,
				Runtime.getRuntime().availableProcessors() / 2);
		if (args.length >= 1) {
			String threadStr = System.getProperty("eclipse.svg.renderthreads");
			if (threadStr != null) {
				try {
					threads = Integer.parseInt(threadStr);
				} catch (Exception e) {
					e.printStackTrace();
					System.out
							.println("Could not parse thread count, using default thread count");
				}
			}
		} else {
			threads = (threads + 1) * 2;
		}

		long totalStartTime = System.currentTimeMillis();

		RasterizerUtil rasterizer = new RasterizerUtil(threads);

		File mavenTargetDir = new File("target/");
		File resources = new File("src/main/resources/");

		for (File file : resources.listFiles()) {
			String dirName = file.getName();
			File outputBase = new File(mavenTargetDir, dirName);

			rasterizer.gatherIcons(dirName, file, file, outputBase);
		}

		System.out.println("Rendering icons with " + threads + " threads.");
		long startTime = System.currentTimeMillis();
		rasterizer.rasterizeAll();

		// Account for each output size and the gray icons
		int fullIconCount = rasterizer.getIconsRendered();
		System.out.println(fullIconCount + " Icons Rendered, Took: "
				+ (System.currentTimeMillis() - startTime) + " ms.");

		// Render a gallery at a few different icon sizes (using the previously
		// rendered icons
		startTime = System.currentTimeMillis();
		System.out.println("Rendering icon galleries...");
		rasterizer.renderGalleries(mavenTargetDir, 16, 800);
		// rasterizer.renderGalleries(mavenTargetDir, 32, 800);
		System.out.println("Icon Galleries Rendered, Took: "
				+ (System.currentTimeMillis() - startTime) + " ms.");

		System.out.println("Rasterization operations completed, Took: "
				+ (System.currentTimeMillis() - totalStartTime) + " ms.");

		System.exit(0);
	}

}