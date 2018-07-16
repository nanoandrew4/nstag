package nsteg.processors;

import nsteg.Spinner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageProcessor {
	public static void writeEncodedImageToDisk(BufferedImage encImg, String outName) {
		try {
			Spinner.end();
			System.out.println();
			Spinner.printWithSpinner("Writing encoded image to disk... ");
			String[] fileNameSplit = outName.split("\\.");
			ImageIO.write(encImg, fileNameSplit[fileNameSplit.length - 1], new File(outName));
			Spinner.end();

			System.out.println("Data encoded successfully into image: \"" + outName + "\"");
		} catch (IOException e) {
			System.err.println("Writing image to disk failed");
		}
	}
}
