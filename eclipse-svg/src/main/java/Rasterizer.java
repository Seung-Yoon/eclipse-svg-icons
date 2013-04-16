import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.batik.transcoder.ErrorHandler;
import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;


public class Rasterizer {

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
        	System.out.println("Rasterizing...");
        	t.transcode(tinput, output);
        	System.out.println("Complete.");
	
	        stream.flush();
	        stream.close();
        } catch(Exception e) {
        	e.printStackTrace();
        } finally {
        }
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File antUi = new File("src/main/resources/org.eclipse.ant.ui/icons/full/");
		File buildfile = new File(antUi, "obj16/ant_buildfile.svg");
		
		
		
		try {
			rastersizeSVG(16, 16, new FileInputStream(buildfile), new FileOutputStream("/home/tmccrary/testsvg0.png"));
			rastersizeSVG(32, 32, new FileInputStream(buildfile), new FileOutputStream("/home/tmccrary/testsvg1.png"));
			rastersizeSVG(64, 64, new FileInputStream(buildfile), new FileOutputStream("/home/tmccrary/testsvg2.png"));
			rastersizeSVG(128, 128, new FileInputStream(buildfile), new FileOutputStream("/home/tmccrary/testsvg3.png"));
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
