import com.google.crypto.tink.Config;
import com.google.crypto.tink.aead.AeadConfig;
import nstag.nStag;
import nstag.nStagImg;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Scanner;

public class Main {
	public static void main(String... args) {
		try {
			Config.register(AeadConfig.TINK_1_1_0);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

		BufferedImage orig, enc;
		try {
			orig = ImageIO.read(new File("test.png"));
			enc = ImageIO.read(new File("outtest.png"));
			Color origC = new Color(orig.getRGB(0, 0));
			Color encC = new Color(enc.getRGB(0, 0));
			System.out.println(origC.getAlpha() % 2 + " " + origC.getRed() % 2 + " " + origC.getGreen() % 2 + " " + origC.getBlue() % 2);
			System.out.println(encC.getAlpha() % 2 + " " + encC.getRed() % 2 + " " + encC.getGreen() % 2 + " " + encC.getBlue() % 2);
		} catch (IOException e) {
			e.printStackTrace();
		}

//		byte[] b = nStag.intToBitArray(255, 8, false);
//		System.out.println(nStag.bitArrayToInt(b, false));

		int opt;
		while (true) {
			Scanner in = new Scanner(System.in);
			System.out.println("Welcome to nstag - A program that hides files inside images");
			System.out.println("Choose one of the following:");
			System.out.println("1. Encode a file inside an image");
			System.out.println("2. Decode a file from an image");
			System.out.println("0. Exit nstag");
			System.out.print("\n>> ");

			do {
				opt = in.nextInt();
			} while (opt < 0 || opt > 2);

			in.nextLine();

			switch (opt) {
				case 1:
					String origImagePath, fileToHide, outImagePath;
					System.out.print("Path to image to hide data in: ");
					origImagePath = in.nextLine();
					System.out.print("Path to file to be hidden: ");
					fileToHide = in.nextLine();
					System.out.print("Desired path and filename (with file extension) for output image: ");
					outImagePath = in.nextLine();
					System.out.print("Number of bits to use in each channel (1-8): ");
					int bitsToUse;
					do {
						bitsToUse = in.nextInt();
					} while (bitsToUse < 1 || bitsToUse > 8);
					nStagImg.encode(origImagePath, fileToHide, outImagePath, bitsToUse);
					break;
				case 2:
					String encodedImgPath, outFilePath;
					System.out.print("Path to image to decode data from: ");
					encodedImgPath = in.nextLine();
					System.out.print("Path and file name under which to save decoded data (with appropriate extension): ");
					outFilePath = in.nextLine();
					nStagImg.decode(encodedImgPath, outFilePath);
					break;
				default:
					return;
			}
		}
	}
}