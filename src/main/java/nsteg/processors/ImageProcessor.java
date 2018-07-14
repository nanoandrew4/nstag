package nsteg.processors;

import nsteg.Spinner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageProcessor {
	public static void writeEncodedImageToDisk(BufferedImage encImg, String outName) {
		try {
			Spinner.printWithSpinner("Writing encoded image to disk... ");
			String[] nameSplit = outName.split("\\.");
			ImageIO.write(encImg, nameSplit[nameSplit.length - 1], new File(outName));
			Spinner.end();

			System.out.println("\nData encoded successfully into image: \"" + outName + "\"");
		} catch (IOException e) {
			System.err.println("Writing image to disk failed");
		}
	}
}
