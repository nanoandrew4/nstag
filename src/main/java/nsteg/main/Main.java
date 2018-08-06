package nsteg.main;

import nsteg.decoders.Decoder;
import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.FileType;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {
	private static Scanner in = new Scanner(System.in);

	private static FileType inFileType = FileType.UNKNOWN;

	public static void main(String[] args) {
		if (args.length > 0) {
			CLIParser.parse(args);
			return;
		}

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

			in.nextLine(); // Catch any excess input

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
		String origImagePath, outImagePath;
		String[] filesToHide;

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Please enter the filename of the image or audio file that will be used to hide a file.");
		System.out.println("Supported image types: " + Encoder.inImgFormats.toString());
		System.out.println("Supported audio types: " + Encoder.inAudFormats.toString());
		System.out.print(">> ");
		origImagePath = getMediaInputFile();

		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Enter the name of the file(s) to be hidden.");
		System.out.println("For entering multiple files, please delimit the filenames with commas (,).");
		System.out.print(">> ");
		filesToHide = getFilesToHide();

		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Desired name for output media file, containing the hidden file, with the appropriate file extension.");
		if (inFileType == FileType.IMAGE)
			System.out.println("Supported image output types: " + Encoder.outImgFormats.toString());
		else if (inFileType == FileType.AUDIO)
			System.out.println("Supported audio output types: " + Encoder.outAudFormats.toString());

		System.out.print(">> ");
		outImagePath = getOutMediaFile();

		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("A smaller value will cause less distortion, but less data will fit.");
		if (inFileType == FileType.IMAGE)
			System.out.println("Recommended: 1-3");
		else if (inFileType == FileType.AUDIO)
			System.out.println("Recommended: 1-5");
		System.out.print("Number of least significant bits to use (1-8): ");
		int bitsToUse;
		do {
			bitsToUse = in.nextInt();
		} while (bitsToUse < 1 || bitsToUse > 8);

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		Encoder.encode(origImagePath, filesToHide, outImagePath, bitsToUse, null, null);
	}

	private static void decode() {
		String encodedImgPath;

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Enter the filename of the image or audio file that you wish to decode data from.");
		System.out.println("Supported image types: " + Encoder.outImgFormats.toString());
		System.out.println("Supported audio types: " + Encoder.outAudFormats.toString());
		System.out.print(">> ");
		encodedImgPath = getEncodedInputFile();

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		Decoder.decode(encodedImgPath, null, null);
	}

	private static String getMediaInputFile() {
		String file = null;
		String fileExt;

		// Check that the file exists and has a file extension that can be opened
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

		inFileType = FileType.getFileType(fileExt);

		return file;
	}

	private static String getEncodedInputFile() {
		String file = null;
		String fileExt;

		// Check that the file exists and the file extension is one that belongs to a lossless codec
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

	private static String[] getFilesToHide() {
		String[] files = null;

		boolean valid;
		do {
			if (files != null) {
				System.err.println("One of the files does not exist, please try again.");
				System.out.print(">> ");
			}

			files = in.nextLine().split(",");
			valid = true;
			for (String fName : files)
				if (!Files.exists(Paths.get(fName.trim())))
					valid = false;
		} while (!valid);

		return files;
	}

	private static String getOutMediaFile() {
		String file = null;
		String fileExt;

		// Prevent user from choosing an image as an encoding medium, and trying to make the output an audio file, or viceversa
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