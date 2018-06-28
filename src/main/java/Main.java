import com.google.crypto.tink.Config;
import com.google.crypto.tink.aead.AeadConfig;
import nstag.ImgDecoder;
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

		BufferedImage orig, encImg;
		try {
			orig = ImageIO.read(new File("test.png"));
			encImg = ImageIO.read(new File("outtest.png"));
			for (int i = 2; i < 5; i++) {
				Color o = new Color(orig.getRGB(i, 0));
				Color c = new Color(encImg.getRGB(i, 0));
				byte[] or = nStag.intToBitArray(o.getRed(), 8, false);
				byte[] og = nStag.intToBitArray(o.getGreen(), 8, false);
				byte[] ob = nStag.intToBitArray(o.getBlue(), 8, false);
				for (byte b : or)
					System.out.print(b);
				System.out.print(" ");
				for (byte b : og)
					System.out.print(b);
				System.out.print(" ");
				for (byte b : ob)
					System.out.print(b);
				System.out.println();

				byte[] mr = nStag.intToBitArray(c.getRed(), 8, false);
				byte[] mg = nStag.intToBitArray(c.getGreen(), 8, false);
				byte[] mb = nStag.intToBitArray(c.getBlue(), 8, false);
				for (byte b : mr)
					System.out.print(b);
				System.out.print(" ");
				for (byte b : mg)
					System.out.print(b);
				System.out.print(" ");
				for (byte b : mb)
					System.out.print(b);
				System.out.println();
				System.out.println();
			}
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