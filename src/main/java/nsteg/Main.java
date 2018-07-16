package nsteg;

import nsteg.decoders.Decoder;
import nsteg.encoders.Encoder;
import nsteg.encoders.FileType;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {
	private static Scanner in = new Scanner(System.in);

	private static FileType inFileType = FileType.UNKNOWN;

	public static void main(String... args) {
		int opt;
		while (true) {
			System.out.println("Welcome to nsteg - A program that hides files inside images or audio files");
			System.out.println("Pro tip: If you notice the program stalling, give me a bit more of your RAM.");
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
					encode();
					break;
				case 2:
					decode();
					break;
				case 3:
					Benchmark.run();
					break;
				default:
					return;
			}
		}
	}

	private static void encode() {
		String origImagePath, fileToHide, outImagePath;

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Please enter the filename of the image or audio file that will be used to hide a file.");
		System.out.println("Supported image types: " + Encoder.inImgFormats.toString());
		System.out.println("Supported audio types: " + Encoder.inAudFormats.toString());
		System.out.print(">> ");
		origImagePath = getMediaInputFile();

		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Enter the filename of the file that is to be hidden.");
		System.out.print(">> ");
		fileToHide = getFileToHide();

		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Desired filename for output media file, containing the hidden file, with appropriate file extension");
		if (inFileType == FileType.IMAGE)
			System.out.println("Supported image output types: " + Encoder.outImgFormats.toString());
		else if (inFileType == FileType.AUDIO)
			System.out.println("Supported audio output types: " + Encoder.outAudFormats.toString());

		System.out.print(">> ");
		outImagePath = getOutMediaFile();

		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.print("Number of least significant bits to use (1-8): ");
		int bitsToUse;
		do {
			bitsToUse = in.nextInt();
		} while (bitsToUse < 1 || bitsToUse > 8);

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		Encoder.encode(origImagePath, fileToHide, outImagePath, bitsToUse);
	}

	private static void decode() {
		String encodedImgPath, outFilePath;

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Enter the filename of the image or audio file that you wish to decode data from.");
		System.out.println("Supported image types: " + Encoder.outImgFormats.toString());
		System.out.println("Supported audio types: " + Encoder.outAudFormats.toString());
		System.out.print(">> ");
		encodedImgPath = getEncodedInputFile();

		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Enter the filename you wish to store the decoded data under (with appropriate file extension).");
		System.out.print(">> ");
		outFilePath = in.nextLine();

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		Decoder.decode(encodedImgPath, outFilePath);
	}

	private static String getMediaInputFile() {
		String file = null;
		String fileExt;

		do {
			if (file != null) {
				System.err.println("Input file invalid, please choose a supported file.");
				System.out.print(">> ");
			}

			file = in.nextLine();
			String[] fileSplit = file.split("\\.");
			fileExt = fileSplit[fileSplit.length - 1];
		}
		while (!Files.exists(Paths.get(file)) || (!Encoder.inImgFormats.contains(fileExt) && !Encoder.inAudFormats.contains(fileExt)));

		if (Encoder.inImgFormats.contains(fileExt))
			inFileType = FileType.IMAGE;
		else if (Encoder.inAudFormats.contains(fileExt))
			inFileType = FileType.AUDIO;

		return file;
	}

	private static String getEncodedInputFile() {
		String file = null;
		String fileExt;

		do {
			if (file != null) {
				System.err.println("Input file invalid, please choose a supported file type.");
				System.out.print(">> ");
			}

			file = in.nextLine();
			String[] fileSplit = file.split("\\.");
			fileExt = fileSplit[fileSplit.length - 1];
		}
		while (!Files.exists(Paths.get(file)) || (!Encoder.outImgFormats.contains(fileExt) && !Encoder.outAudFormats.contains(fileExt)));

		return file;
	}

	private static String getFileToHide() {
		String file = null;

		do {
			if (file != null) {
				System.err.println("File does not exist, please try again.");
				System.out.print(">> ");
			}

			file = in.nextLine();
		} while (!Files.exists(Paths.get(file)));

		return file;
	}

	private static String getOutMediaFile() {
		String file = null;
		String fileExt;

		do {
			if (file != null) {
				System.err.println("Output file is invalid, please choose a supported file type.");
				System.out.print(">> ");
			}

			file = in.nextLine();
			String[] fileSplit = file.split("\\.");
			fileExt = fileSplit[fileSplit.length - 1];
		}
		while ((inFileType == FileType.AUDIO && !Encoder.outAudFormats.contains(fileExt)) || (inFileType == FileType.IMAGE && !Encoder.outImgFormats.contains(fileExt)));

		return file;
	}
}