package nsteg.encoders;

import nsteg.nsteg_utils.Spinner;
import nsteg.encoders.aud.AudEncoder;
import nsteg.encoders.img.ImgEncoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Compressor;
import nsteg.nsteg_utils.Crypto;
import nsteg.processors.AudioProcessor;
import nsteg.processors.ImageProcessor;

import javax.imageio.ImageIO;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * Abstract class defining methods that any encoder should have present, in order to make the design a bit more
 * flexible. Also carries out the encoding process, when Main or CLIParser request it, through the encode() method.
 * If multiple files are to be encoded, they will all be copied into one byte array, to minimize operations.
 * <p><br>
 * The encoding order is as follows:
 * [# of least significant bits used (4 bits)] - [Number of files encoded (32 bits)] -
 * [File name lengths (32 bits) and file names (Variable size) in following format: fnamelen, fname, fnamelen...] -
 * [Individual file sizes (32 bits each)] -
 * [Compressed size of the continuous byte array containing all files to be encoded (32 bits)] -
 * [Hash salt (if encryption was used) (64 bits)] - [File(s) (Variable size)]
 */
public abstract class Encoder {
	/**
	 * Contains the formats that can be read by ImageIO. Any other image formats are likely not supported natively.
	 */
	public static LinkedList<String> inImgFormats = new LinkedList<>();
	/**
	 * Contains the formats that can be written by ImageIO, and that are lossless. Any other image formats is likely
	 * not supported natively or is lossy, and therefore no good for holding data.
	 */
	public static LinkedList<String> outImgFormats = new LinkedList<>();

	/**
	 * Holds the formats that can be read by AudioSystem. Any other audio format is not supported natively.
	 */
	public static LinkedList<String> inAudFormats = new LinkedList<>();
	/**
	 * Holds the formats that can be written by AudioSystem, and that are lossless. Any other audio format is either
	 * not supported natively or is lossy, and therefore no good for holding data.
	 */
	public static LinkedList<String> outAudFormats = new LinkedList<>();

	/*
	 * Specifies valid image and audio formats. Can be modified if other lossless codecs are implemented.
	 */
	static {
		String[] inImgSuffixes = ImageIO.getReaderFileSuffixes();
		inImgFormats.addAll(Arrays.asList(inImgSuffixes));

		String[] outImgSuffixes = {"png", "bmp", "tif", "tiff"};
		outImgFormats.addAll(Arrays.asList(outImgSuffixes));

		String[] inAudSuffixes = {"mp3", "wav", "flac", "alac"};
		inAudFormats.addAll(Arrays.asList(inAudSuffixes));

		String[] outAudSuffixes = {"wav", "flac"};
		outAudFormats.addAll(Arrays.asList(outAudSuffixes));
	}

	/**
	 * Number of bits used to hold the compressed/uncompressed size of the file being encoded.
	 */
	public final static int SIZE_BITS_COUNT = Integer.SIZE;

	/**
	 * Number of bits used to hold the number of least significant bits used during the encoding process.
	 */
	public final static int LSB_BITS_COUNT = 4;

	protected static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

	// Impl specific
	public abstract void encodeBits(byte[] bits);

	// Impl specific
	public abstract void encodeBytes(byte[] bytes);

	// Impl specific
	public abstract void stopThreads();

	/**
	 * Determines if the file to be hidden actually fits in the media file chosen by the user. If it does not fit,
	 * errors are printed and the encoding process will stop.
	 *
	 * @param fileSizeInBits  Size of the file to be hidden, in bits. Aside from the space required to encode the data
	 *                        itself, one 32 bit chunks are required to encode the compressed size of of the data
	 * @param numOfFiles      Number of files that are to be encoded. This is relevant because each file has its
	 *                        length stored, which occupies 32 bits, as well as its name, whose length also
	 *                        occupies 32 bits. This value is also encoded, using 32 bits
	 * @param fileNameLengths Combined lengths of the names of the files that are to be encoded. This number
	 *                        represents the number of bytes that are required to store all the file names, not bits
	 * @param LSBsToUse       Number of least significant bits to use during the encoding process. This value will be
	 *                        encoded, using 4 bits
	 * @param encrypted       True if encryption is to be used, false otherwise. If true, the AAD data, IV and salt
	 *                        must be written. The number of bits they use can be seen in the Crypto class
	 * @return True if the file will fit inside the media file, false otherwise
	 */
	protected abstract boolean doesFileFit(int fileSizeInBits, int numOfFiles, int fileNameLengths, int LSBsToUse,
										   boolean encrypted);

	/**
	 * Initializes and returns the encoder implementation capable of handling the media file entered by the user.
	 *
	 * @param file      Name of media file to be used for encoding
	 * @param LSBsToUse Number of least significant bits to use during the encoding process
	 * @return Initialized encoder, ready to start encoding data
	 */
	private static Encoder getEncoder(@NotNull String file, int LSBsToUse) {
		Spinner.printWithSpinner("Loading media file to encode file into... ");

		String[] split = file.split("\\.");
		String fileExt = split[split.length - 1];

		Encoder encoder = null;

		if (inImgFormats.contains(fileExt)) {
			try {
				encoder = new ImgEncoder(ImageIO.read(new File(file)), LSBsToUse);
			} catch (IOException ignored) {
			}
		} else if (inAudFormats.contains(fileExt))
			encoder = new AudEncoder(file, LSBsToUse);
		else
			System.err.println("File format not supported.");

		Spinner.end();
		return encoder;
	}

	/**
	 * Encodes any number file(s) into a media file, by inserting the bits of the file(s) into the least significant
	 * bits of the media file. For images, the least significant bits used are those from the RGB or ARGB channels, in
	 * each pixel. For audio files, each byte belonging to the right channel is modified, and the left channel is left
	 * untouched. The file(s) can be optionally encrypted through the use of the AES cipher, using a 256 bit key
	 * derived by scrypt, which is generated by a user chosen password, and a random salt. The file(s) is/are always
	 * compressed before any encryption happens.
	 * <p><br>
	 * Instead of encoding each file individually, the files are loaded into a byte array, which contains all the
	 * files. Since the sizes of each file are encoded too, the files can be separated during the decoding phase.
	 *
	 * @param origMediaPath Path and name to image into which to encode the data
	 * @param filesToEncode Array of strings containing the filenames of the files to be encoded, complete with their
	 *                      respective file extensions
	 * @param outMediaName  Path and name of the desired output image (copy of the original + file data)
	 * @param LSBsToUse     Number of least significant bits to use in each color channel. Using more bits will result
	 *                      in a greater visual deviation from the original. Range is 1-8. Recommended is 1-3
	 * @param encrypt       True to encrypt the file(s), false otherwise. May be null, in which case the user will
	 *                      be asked if they want to encrypt the file(s) or not. This argument exists to allow the
	 *                      program to be non-interactive, which is employed by CLIParser
	 * @param pass          Password to use for encryption. This field may be null, in which case the Crypto.encrypt()
	 *                      method will prompt the user for a password. Nulling this field is the safer approach, since
	 *                      it means the password remains in memory for a much shorter period of time. This argument
	 *                      exists to allow the CLIParser to pass a password in, if the user chooses
	 */
	public static void encode(@NotNull String origMediaPath, @NotNull String[] filesToEncode,
							  @NotNull String outMediaName, int LSBsToUse, Boolean encrypt, String pass) {
		int[] fileSizes = new int[filesToEncode.length];
		byte[] dataBytes = loadFiles(filesToEncode, fileSizes);
		if (dataBytes == null)
			return;

		double origByteSize = dataBytes.length;
		dataBytes = Compressor.compress(dataBytes);

		if (encrypt == null)
			encrypt = Crypto.offerToCrypt(true);

		// Size of all the files being encoded, before compression
		byte[] uncompFileSizeBits = BitByteConv.intToBitArray((int) origByteSize, SIZE_BITS_COUNT);

		// Compressed size of the file(s), accounting for the InitVector and AAD data bits, if encryption is to be used
		int compSize = dataBytes.length + (encrypt ? Crypto.AES_IV_SIZE + Crypto.GCM_AAD_SIZE / Byte.SIZE : 0);
		byte[] compFileSizeBits = BitByteConv.intToBitArray(compSize, Integer.SIZE);

		int fileNameLengths = 0;
		for (String s : filesToEncode)
			fileNameLengths += s.length();

		Encoder encoder = getEncoder(origMediaPath, LSBsToUse);
		if (encoder == null || !encoder.doesFileFit(dataBytes.length * Byte.SIZE, filesToEncode.length,
													fileNameLengths, LSBsToUse, encrypt))
			return;

		byte[] saltBytes = null;
		if (encrypt) {
			// Encrypt the data, and use the uncompressed and compressed sizes of the file(s) as AAD
			byte[][] saltAndDataBits = Crypto.encrypt(dataBytes, Crypto.genAAD(uncompFileSizeBits, compFileSizeBits),
													  pass);
			saltBytes = saltAndDataBits[0]; // Salt bytes, which are the last part of the header to be encoded
			dataBytes = saltAndDataBits[1]; // Encrypted data, with IV and AAD
		}

		Spinner.printWithSpinner("Encoding metadata... ");
		encoder.encodeBits(BitByteConv.intToBitArray(filesToEncode.length, SIZE_BITS_COUNT));
		for (String fileName : filesToEncode) {
			encoder.encodeBits(BitByteConv.intToBitArray(fileName.length(), SIZE_BITS_COUNT));
			encoder.encodeBytes(fileName.getBytes());
		}
		for (int fileSize : fileSizes)
			encoder.encodeBits(BitByteConv.intToBitArray(fileSize, SIZE_BITS_COUNT));
		encoder.encodeBits(compFileSizeBits);

		if (encrypt)
			encoder.encodeBytes(saltBytes);

		Spinner.printWithSpinner("Encoding data to media file... ");
		encoder.encodeBytes(dataBytes);
		encoder.stopThreads();

		if (encoder instanceof ImgEncoder)
			ImageProcessor.writeEncodedImageToDisk(((ImgEncoder) encoder).getImg(), outMediaName);
		else if (encoder instanceof AudEncoder)
			AudioProcessor.writePCMToDisk(outMediaName, (AudEncoder) encoder);

		System.out.println("Done!\n");
	}

	/**
	 * Loads any number of files into a continuous byte array. This array is the one that will be compressed, encrypted
	 * and encoded into the target media file.
	 *
	 * @param filesToEncode Array of strings containing all the filenames of the files that will be encoded in the
	 *                      media file
	 * @param fileSizes     Array of the same size as filesToEncode. The sizes of all the files will be written in this
	 *                      array, as the method loads them in
	 * @return Byte array containing all the files to encode
	 */
	private static byte[] loadFiles(String[] filesToEncode, int[] fileSizes) {
		byte[] dataBytes = {};
		try {
			Spinner.printWithSpinner("Loading file(s) to encode... ");
			byte[] tmpDataBytes, tmp;
			for (int i = 0; i < filesToEncode.length; i++) {
				tmp = Files.readAllBytes(Paths.get(filesToEncode[i].trim()));
				fileSizes[i] = tmp.length;

				tmpDataBytes = new byte[dataBytes.length + tmp.length];
				System.arraycopy(dataBytes, 0, tmpDataBytes, 0, dataBytes.length);
				System.arraycopy(tmp, 0, tmpDataBytes, dataBytes.length, tmp.length);

				dataBytes = tmpDataBytes;
			}
		} catch (IOException e) {
			System.err.println("Error loading file(s) to be hidden");
			return null;
		}
		return dataBytes;
	}
}
