import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Hack that generates E4 workbench edit icons.
 * 
 * @author tmccrary
 *
 */
public class E4EditStarGenerator {

	private final static String STAR_BORDER_LIGHT = "${star_border_light";
	private final static String STAR_BORDER_DARK = "${star_border_dark";

	private final static String STAR_INNER_1 = "${star_inner_1";
	private final static String STAR_INNER_2 = "${star_inner_2";
	private final static String STAR_INNER_3 = "${star_inner_3";
	private final static String STAR_INNER_4 = "${star_inner_4";
	private final static String STAR_INNER_5 = "${star_inner_5";

	/**
	 * 
	 * @param dir
	 * @param template
	 * @param output
	 */
	private static void generateIcons(File dir, File template, File output) {
		String templateData = readTemplate(template);
		
		if(templateData == null) {
			return;
		}
		
		for(File child : dir.listFiles()) {
			if(child.isDirectory()) {
				continue;
			}
			
			if(!child.getName().startsWith("Create")) {
				continue;
			}
			
			String svgName = child.getName().replace("gif", "svg");
			
			File childOutput = new File(output, svgName);
			
			if(!childOutput.exists()) {
				String newStar = new String(templateData);
				for(int k=1; k<4; k++) {
					newStar = generateNewStar(k, newStar);
				}
				
				PrintWriter writer = null;
				try {
					writer = new PrintWriter(childOutput);
					writer.write(newStar);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					writer.close();
				}
			}
		}
		
	}
	
	/**
	 * 
	 * @return
	 */
	public static Color getRandomColor() {
		int R = (int)(Math.random()*256);
		int G = (int)(Math.random()*256);
		int B= (int)(Math.random()*256);
		return new Color(R, G, B);
	}
	
	/**
	 * 
	 * @param input
	 * @param brightness
	 * @return
	 */
	private static String adjustBrightness(Color input, float brightness) {
		float[] strokeHsb = new float[3];
		Color.RGBtoHSB(input.getRed(), input.getGreen(), input.getBlue(), strokeHsb);
		strokeHsb[2] = brightness;
		Color strokeColor = new Color(Color.HSBtoRGB(strokeHsb[0], strokeHsb[1], strokeHsb[2]));

		String lightRed = Integer.toHexString(strokeColor.getRed());
		String lightGreen = Integer.toHexString(strokeColor.getGreen());
		String lightBlue = Integer.toHexString(strokeColor.getBlue());
		
		StringBuilder lightBuilder = new StringBuilder();
			lightBuilder.append("#").append(lightRed).append(lightGreen).append(lightBlue);
		String lightString = lightBuilder.toString();
		
		return lightString;
	}
	
	private static String getHex(int input) {
		String hexString = Integer.toHexString(input);
		if(hexString.length() == 1) {
			hexString = "0" + hexString;
		}
		
		return hexString;
	}
	
	/**
	 * 
	 * @param templateData
	 * @return
	 */
	private static String generateNewStar(int id, String templateData) {
		Color baseColor = getRandomColor();
		
		float[] fillHsb = new float[3];
		Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), fillHsb);
		fillHsb[2] = 0.9f;
		Color fillColor = new Color(Color.HSBtoRGB(fillHsb[0], fillHsb[1], fillHsb[2]));

		float[] strokeHsb = new float[3];
		Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), strokeHsb);
		strokeHsb[1] = 0.7f;
		strokeHsb[2] = 0.7f;
		Color strokeColor = new Color(Color.HSBtoRGB(strokeHsb[0], strokeHsb[1], strokeHsb[2]));
		
		float[] lightHsb = new float[3];
		float[] darkHsb = new float[3];
		Color.RGBtoHSB(strokeColor.getRed(), strokeColor.getGreen(), strokeColor.getBlue(), lightHsb);
		Color.RGBtoHSB(strokeColor.getRed(), strokeColor.getGreen(), strokeColor.getBlue(), darkHsb);
		
		lightHsb[2] = 0.8f;
		darkHsb[2] = 0.5f;
		
		Color light = new Color(Color.HSBtoRGB(lightHsb[0], lightHsb[1], lightHsb[2]));
		Color dark = new Color(Color.HSBtoRGB(darkHsb[0], darkHsb[1], darkHsb[2]));
		
		String lightRed = getHex(light.getRed());
		String lightGreen = getHex(light.getGreen());
		String lightBlue = getHex(light.getBlue());
		
		StringBuilder lightBuilder = new StringBuilder();
			lightBuilder.append("#").append(lightRed).append(lightGreen).append(lightBlue);
		String lightString = lightBuilder.toString();
		
		
		String darkRed = getHex(dark.getRed());
		String darkGreen = getHex(dark.getGreen());
		String darkBlue = getHex(dark.getBlue());

		StringBuilder darkBuilder = new StringBuilder();
			darkBuilder.append("#").append(darkRed).append(darkGreen).append(darkBlue);
		String darkString = darkBuilder.toString();

		System.out.println(lightString + " " + darkString);
		
		String suffix = "_" + id + "}";
		templateData = templateData.replace(STAR_BORDER_LIGHT + suffix, lightString);
		templateData = templateData.replace(STAR_BORDER_DARK + suffix, darkString);
		templateData = templateData.replace(STAR_INNER_1 + suffix, adjustBrightness(fillColor, 2f));
		templateData = templateData.replace(STAR_INNER_2 + suffix, adjustBrightness(fillColor, 0.9f));
		templateData = templateData.replace(STAR_INNER_3 + suffix, adjustBrightness(fillColor, 0.8f));
		templateData = templateData.replace(STAR_INNER_4 + suffix, adjustBrightness(fillColor, 0.7f));
		templateData = templateData.replace(STAR_INNER_5 + suffix, adjustBrightness(fillColor, 0.65f));
		
		return templateData;
	}

	private static String readTemplate(File template) {
		BufferedReader bufReader = null;
		try {
			FileReader reader = new FileReader(template);
			bufReader = new BufferedReader(reader);

			String line = bufReader.readLine();
			StringBuilder builder = new StringBuilder();
			
			while(line != null) {
				builder.append(line);
				builder.append("\n");
				line = bufReader.readLine();
			}
			
			return builder.toString();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				bufReader.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return null;
	}

	public static void main(String[] args) {
		File root = new File("src/main/resources/eclipse.platform.ui/bundles/org.eclipse.e4.ui.model.workbench.edit/icons/full/ctool16");
		File template = new File("reference/star.svg");
		
		generateIcons(root, template, root);
	}

}
