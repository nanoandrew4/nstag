package nsteg.main;

import nsteg.decoders.Decoder;
import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.FileType;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Class for parsing command line arguments, basically non-interactive mode.
 */
public class CLIParser {
	private static FileType fileType;

	/**
	 * Determines what will be done with the arguments passed, and passes them on to the appropriate method.
	 *
	 * @param args Arguments to parse
	 */
	static void parse(String[] args) {
		fileType = FileType.UNKNOWN;
		if (args.length == 1 && "--help".equalsIgnoreCase(args[0]))
			displayHelp();
		else if (args.length >= 9 && "-e".equals(args[0]))
			encode(args);
		else if (args.length >= 5 && "-d".equals(args[0]))
			decode(args);
		else
			System.err.println(
					"Input is not valid. For help on running the program from the command line, use the --help flag"
			);
	}

	// Designed to fit perfectly in a standard 80x24 Linux terminal window
	private static void displayHelp() {
		// Trim [] off the strings, since LinkedList.toString() adds them
		String supportedInImgFormats = Encoder.inImgFormats.toString();
		supportedInImgFormats = supportedInImgFormats.substring(1, supportedInImgFormats.length() - 1);

		String supportedInAudFormats = Encoder.inAudFormats.toString();
		supportedInAudFormats = supportedInAudFormats.substring(1, supportedInAudFormats.length() - 1);

		String supportedOutImgFormats = Encoder.outImgFormats.toString();
		supportedOutImgFormats = supportedOutImgFormats.substring(1, supportedOutImgFormats.length() - 1);

		String supportedOutAudFormats = Encoder.outAudFormats.toString();
		supportedOutAudFormats = supportedOutAudFormats.substring(1, supportedOutAudFormats.length() - 1);

		System.out.println("Format: java -jar nsteg-vX.Y-release.jar [mode] [mode arguments] [optional arguments]");
		System.out.println("Modes:");
		System.out.println("\t-e: Encode (Hide file(s) in a media file)");
		System.out.println("\t-d: Decode (Extract file(s) from a media file)");
		System.out.println("--------------------------------------------------------------------------------");
		System.out.println("Encode arguments:");
		System.out.println(
				"\t--mif: Media input file. This is the image or audio file that will be \n" +
				"\t       used to hide the data.\n" +
				"\t       Supported image codecs: " + supportedInImgFormats + "\n" +
				"\t       Supported audio codecs: " + supportedInAudFormats + "\n"
		);
		System.out.println(
				"\t--hf:  File(s) to hide. To encode (hide) multiple files, delimit the\n" +
				"\t       files with commas, and do not use spaces between filenames or \n" +
				"\t       commas. Eg: \"test.txt,test.pdf,test.ppt\".\n"
		);
		System.out.println(
				"\t--mof: Media output file. The media file with the encoded data will be\n" +
				"\t       saved under this name. Must be a supported file type, and a\n" +
				"\t       lossless codec.\n" +
				"\t       Supported image codecs: " + supportedOutImgFormats + "\n" +
				"\t       Supported audio codecs: " + supportedOutAudFormats + "\n"
		);
		System.out.println(
				"\t--lsb: Number of least significant bits to use for encoding. A larger\n" +
				"\t       number will allow more data to fit, but cause more distortion.\n" +
				"\t       Valid range is 1-8, both inclusive.\n"
		);
		System.out.println("Decode arguments:");
		System.out.println(
				"\t--mif: Media input file. This is the image or audio file from which\n" +
				"\t       nsteg will attempt to decode files.\n" +
				"\t       Supported image codecs: " + supportedOutImgFormats + "\n" +
				"\t       Supported audio codecs: " + supportedOutAudFormats + "\n"
		);
		System.out.println(
				"\t--hf:  File names to assign to the decoded files. They will be decoded\n" +
				"\t       in the same order they were encoded. If there are insufficient\n" +
				"\t       names, the program will assign names in the following way:\n" +
				"\t       \"nstegDecFile[file num].unknown\n"
		);
		System.out.println("Optional arguments:");
		System.out.println(
				"\t--enc:  Valid values are \"true\" and \"false\". Used to determine whether\n" +
				"\t        encryption will be used when encoding. If this flag is not used,\n" +
				"\t        the program will ask the user if the data should be encrypted.\n"
		);
		System.out.println(
				"\t--pass: Passphrase to encrypt the data with, if encryption is used.\n" +
				"\t        Setting this field carries a security risk, since the password\n" +
				"\t        remains in memory longer than if this field was not set, in \n" +
				"\t        which case the program will prompt it right before it is\n" +
				"\t        necessary, and wipe it from memory once the encryption is done."
		);
		System.out.println("--------------------------------------------------------------------------------");
		System.out.println(
				"Sample encoding use:\n" +
				"\t\"java -jar nsteg-vX.Y-release.jar -e --mif test.jpg\n" +
				"\t --hf test.txt,test.pdf --mof outtest.png --lsb 3\"\n"
		);
		System.out.println(
				"Sample decoding use:\n" +
				"\t\"java -jar nsteg-vX.Y-release.jar -d --mif outtest.png\n" +
				"\t --hf test.txt,test.pdf\"\n"
		);
		System.out.println(
				"Sample encryption use:\n" +
				"\t\"java -jar nsteg-vX.Y-release.jar -e --mif test.jpg --hf test.txt\n" +
				"\t --mof outtest.png --lsb 2 --enc true --pass thebestpassword\""
		);
	}

	/**
	 * Parses the required arguments from the ones provided by Main, and then attempts to encode with the variables
	 * given.
	 *
	 * @param args Arguments to parse
	 */
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
				if (fileType == FileType.UNKNOWN
					|| (fileType == FileType.IMAGE && !Encoder.inImgFormats.contains(fileExt))
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

		if (mediaInputFile.length() > 0 && mediaOutputFile.length() > 0 && filesToHide.length == 0 && LSBsToUse > 0) {
			System.out.println();
			Encoder.encode(mediaInputFile, filesToHide, mediaOutputFile, LSBsToUse, encrypt, pass);
		} else
			System.err.println("Arguments missing. For help, please use the --help flag. Exiting.");
	}

	/**
	 * Parses the required arguments from the ones provided by Main, and then attempts to decode with the variables
	 * given.
	 *
	 * @param args Arguments to parse
	 */
	private static void decode(String[] args) {
		String encodedInputFile = "", pass = null;
		String[] filesToDecode = {};
		Boolean encrypt = null;

		for (int i = 1; i < args.length; i += 2) {
			if ("--mif".equals(args[i]) && Files.exists(Paths.get(args[i + 1]))) {
				String[] fNameSplit = args[i + 1].split("\\.");
				String fileExt = fNameSplit[fNameSplit.length - 1];
				fileType = FileType.getFileType(fileExt);

				if (fileType == FileType.UNKNOWN
					|| (fileType == FileType.IMAGE && !Encoder.outImgFormats.contains(fileExt))
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

		if (encodedInputFile.length() > 0 && filesToDecode.length > 0) {
			System.out.println();
			Decoder.decode(encodedInputFile, filesToDecode, encrypt, pass);
		} else
			System.err.println("Arguments missing. For help, please use the --help flag. Exiting.");
	}
}
