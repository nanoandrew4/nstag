package nsteg;

import nsteg.decoders.Decoder;
import nsteg.encoders.Encoder;

import java.util.Scanner;

public class Main {
	public static void main(String... args) {
		int opt;
		while (true) {
			Scanner in = new Scanner(System.in);
			System.out.println("Welcome to nsteg - A program that hides files inside images or audio files");
			System.out.println("Choose one of the following:");
			System.out.println("1. Encode data");
			System.out.println("2. Decode data");
			System.out.println("3. Benchmark encoding/decoding speed");
			System.out.println("0. Exit nsteg");
			System.out.print("\n>> ");

			do {
				opt = in.nextInt();
			} while (opt < 0 || opt > 3);

			in.nextLine();

			switch (opt) {
				case 1:
					String origImagePath, fileToHide, outImagePath;
					System.out.print("Path to media file to hide data in: ");
					origImagePath = in.nextLine();
					System.out.print("Path to file to be hidden: ");
					fileToHide = in.nextLine();
					System.out.print("Desired filename for encoded media file: ");
					outImagePath = in.nextLine();
					System.out.print("Number of least significant bits to use (1-8): ");
					int bitsToUse;
					do {
						bitsToUse = in.nextInt();
					} while (bitsToUse < 1 || bitsToUse > 8);
					Encoder.encode(origImagePath, fileToHide, outImagePath, bitsToUse);
					break;
				case 2:
					String encodedImgPath, outFilePath;
					System.out.print("Path to media file to decode data from: ");
					encodedImgPath = in.nextLine();
					System.out.print("Path and file name under which to save decoded data (with appropriate extension): ");
					outFilePath = in.nextLine();
					Decoder.decode(encodedImgPath, outFilePath);
					break;
				case 3:
					Benchmark.run();
					break;
				default:
					return;
			}
		}
	}
}