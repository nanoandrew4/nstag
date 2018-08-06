package nsteg.processors;

import nsteg.nsteg_utils.Spinner;

import javax.imageio.ImageIO;
import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Handles writing images to disk.
 */
public class ImageProcessor {

	/**
	 * Writes a BufferedImage to a file, using a user specified lossless codec. The codec is determined by looking at
	 * the file extension on the output name, and is verified in Main before the encoding process begins.
	 *
	 * @param encImg  BufferedImage to be written
	 * @param outName Desired name for the output file
	 */
	public static void writeEncodedImageToDisk(@NotNull BufferedImage encImg, @NotNull String outName) {
		try {
			Spinner.end();
			System.out.println();
			Spinner.printWithSpinner("Writing encoded image to disk... ");
			String[] fileNameSplit = outName.split("\\.");
			String fileExt = fileNameSplit[fileNameSplit.length - 1];

			ImageIO.write(encImg, fileExt, new File(outName));

			Spinner.end();
			System.out.println("Data encoded successfully into image: \"" + outName + "\"");
		} catch (IOException e) {
			System.err.println("Writing image to disk failed");
		}
	}
}
