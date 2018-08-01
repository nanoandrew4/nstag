package nsteg.main;

import nsteg.decoders.Decoder;
import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.FileType;

import java.nio.file.Files;
import java.nio.file.Paths;

public class CLIParser {
	private static FileType fileType;

	static void parse(String[] args) {
		fileType = FileType.UNKNOWN;
		if (args.length == 1 && "--help".equalsIgnoreCase(args[0]))
			displayHelp();
		else if (args.length >= 9 && "-e".equals(args[0]))
			encode(args);
		else if (args.length >= 5 && "-d".equals(args[0]))
			decode(args);
		else
			System.err.println("Input is not valid. For help on running the program from the command line, use the --help flag");
	}

	private static void displayHelp() {

	}

	private static void encode(String[] args) {
		String mediaInputFile = "", mediaOutputFile = "", pass = null;
		String[] filesToHide = {};
		int LSBsToUse = 0;
		Boolean encrypt = null;
		for (int i = 1; i < args.length; i += 2) {
			if ("--mif".equals(args[i]) && Files.exists(Paths.get(args[i + 1]))) {
				String[] fNameSplit = args[i + 1].split("\\.");
				String fileExt = fNameSplit[fNameSplit.length - 1];
				fileType = FileType.getFileType(fileExt);
				if (fileType == FileType.UNKNOWN || (fileType == FileType.IMAGE && !Encoder.inImgFormats.contains(fileExt))
						|| (fileType == FileType.AUDIO && !Encoder.inAudFormats.contains(fileExt))) {
					System.err.println("Input media file is invalid, please see supported file types. Exiting.");
					return;
				}

				mediaInputFile = args[i + 1];
			} else if ("--hf".equals(args[i])) {
				filesToHide = args[i + 1].split(",");

				for (String fName : filesToHide)
					if (!Files.exists(Paths.get(fName))) {
						System.err.println("The file \"" + fName + "\" does not exits... Exiting.");
						return;
					}
			} else if ("--mof".equals(args[i])) {
				String[] fNameSplit = args[i + 1].split("\\.");
				String fileExt = fNameSplit[fNameSplit.length - 1];
				if ((fileType == FileType.AUDIO && !Encoder.outAudFormats.contains(fileExt))
						|| (fileType == FileType.IMAGE && !Encoder.outImgFormats.contains(fileExt))) {
					System.err.println("Input type mismatch. Please ensure you encode from an image to an image" +
							"or from an audio file to an audio file. Exiting.");
					return;
				}

				mediaOutputFile = args[i + 1];
			} else if ("--lsb".equals(args[i])) {
				try {
					LSBsToUse = Integer.valueOf(args[i + 1]);
				} catch (Exception e) {
					System.err.println("LSB must be a numeric value. Exiting.");
					return;
				}

				if (LSBsToUse < 1 || LSBsToUse > 8) {
					System.err.println("LSB must be between 1-8. Exiting.");
					return;
				}
			} else if ("--enc".equals(args[i]))
				encrypt = Boolean.valueOf(args[i + 1]);
			else if ("--pass".equals(args[i]) && encrypt != null && encrypt)
				pass = args[i + 1];
			else {
				System.err.println("Unknown option \"" + args[i] + "\". Exiting.");
				return;
			}
		}

		System.out.println();
		Encoder.encode(mediaInputFile, filesToHide, mediaOutputFile, LSBsToUse, encrypt, pass);
	}

	private static void decode(String[] args) {
		String encodedInputFile = "", pass = null;
		String[] filesToDecode = {};
		Boolean encrypt = null;

		for (int i = 1; i < args.length; i += 2) {
			if ("--mif".equals(args[i]) && Files.exists(Paths.get(args[i + 1]))) {
				String[] fNameSplit = args[i + 1].split("\\.");
				String fileExt = fNameSplit[fNameSplit.length - 1];
				fileType = FileType.getFileType(fileExt);

				if (fileType == FileType.UNKNOWN || (fileType == FileType.IMAGE && !Encoder.outImgFormats.contains(fileExt))
						|| (fileType == FileType.AUDIO && !Encoder.outAudFormats.contains(fileExt))) {
					System.err.println("Input media file is invalid, please see supported file types. Exiting.");
					return;
				}
				encodedInputFile = args[i + 1];
			} else if ("--hf".equals(args[i]))
				filesToDecode = args[i + 1].split(",");
			else if ("--enc".equals(args[i]))
				encrypt = Boolean.valueOf(args[i + 1]);
			else if ("--pass".equals(args[i]) && encrypt != null && encrypt)
				pass = args[i + 1];
			else {
				System.err.println("Unknown option \"" + args[i] + "\". Exiting.");
				return;
			}
		}

		System.out.println();
		Decoder.decode(encodedInputFile, filesToDecode, encrypt, pass);
	}
}
