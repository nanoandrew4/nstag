import com.google.crypto.tink.Config;
import com.google.crypto.tink.aead.AeadConfig;
import nstag.nStagImg;

import java.security.GeneralSecurityException;
import java.util.Scanner;

public class Main {
	public static void main(String... args) {
		try {
			Config.register(AeadConfig.TINK_1_1_0);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

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
					System.out.print("Path to file in which to save decoded data (with appropriate extension): ");
					outFilePath = in.nextLine();
					nStagImg.decode(encodedImgPath, outFilePath);
					break;
				default:
					return;
			}
		}
	}
}