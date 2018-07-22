package nsteg.encoders;

import nsteg.Spinner;
import nsteg.encoders.aud.AudioEncoder;
import nsteg.encoders.img.ImgEncoderThread;
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
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * Abstract class defining methods that any encoder should have present, in order to make the design a bit more flexible.
 * Also carries out the encoding process, when Main requests it, through the encode() method.
 * <p><br>
 * The encoding order is as follows:
 * [# of least significant bits used (4 bits)] - [Compressed file size (32 bits)] - [Uncompressed file size (32 bits)] -
 * [Hash salt (if encryption was used) (64 bits)] - [File (Variable size)]
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
	 * Holds the formats that can be written by AudioSystem, and that are lossless. Any other audio format is either not
	 * supported natively or is lossy, and therefore no good for holding data.
	 */
	public static LinkedList<String> outAudFormats = new LinkedList<>();

	/*
	 * Specifies valid image and audio formats. Can be modified if other lossless codecs are implemented.
	 */
	static {
		String[] inImgSuffixes = ImageIO.getReaderFileSuffixes();
		inImgFormats.addAll(Arrays.asList(inImgSuffixes));
		inImgFormats.add("tif");
		inImgFormats.add("tiff");

		String[] outImgSuffixes = {"png", "bmp", "tif", "tiff"};
		outImgFormats.addAll(Arrays.asList(outImgSuffixes));

		String[] inAudSuffixes = {"mp3", "wav", "flac", "alac"};
		inAudFormats.addAll(Arrays.asList(inAudSuffixes));

		String[] outAudSuffixes = {"wav", "flac", "alac"};
		outAudFormats.addAll(Arrays.asList(outAudSuffixes));
	}

	/**
	 * Number of bits used to hold the compressed/uncompressed size of the file being encoded.
	 */
	public final static int SIZE_BITS_COUNT = 4 * Byte.SIZE;

	/**
	 * Number of bits used to hold the number of least significant bits used during the encoding process.
	 */
	public final static int LSB_BITS_COUNT = 4;

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

	// Impl specific
	public abstract void encodeBits(byte[] bits);

	// Impl specific
	public abstract void encodeBytes(byte[] bytes);

	/**
	 * Determines if the file to be hidden actually fits in the media file chosen by the user. If it does not fit,
	 * errors are printed and the encoding process will stop.
	 *
	 * @param fileSizeInBits Size of the file to be hidden, in bits
	 * @param LSBsToUse      Number of least significant bits to use during the encoding process
	 * @param encrypted      True if encryption is to be used, false otherwise
	 * @return True if the file will fit inside the media file, false otherwise
	 */
	public abstract boolean doesFileFit(int fileSizeInBits, int LSBsToUse, boolean encrypted);

	// Impl specific
	public abstract void stopThreads();

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
			encoder = new AudioEncoder(file, LSBsToUse);
		else
			System.err.println("File format not supported.");

		Spinner.end();
		return encoder;
	}

	/**
	 * Encodes a file into a media file, by inserting the bits of the file into the least significant bits of the media
	 * file. For images, the least significant bits used are those from the RGB or ARGB channels, in each pixel. For
	 * audio files, each byte belonging to the right channel is modified, and the left channel is left untouched.
	 * The file can be optionally encrypted through the use of the AES cipher, using a 256 bit key derived by scrypt,
	 * which is generated by a user chosen password, and a random salt. The file is always compressed before any
	 * encryption happens.
	 *
	 * @param origMediaPath Path and name to image into which to encode the data
	 * @param fileToEncode  File to be encoded into the image
	 * @param outMediaName  Path and name of the desired output image (copy of the original + file data)
	 * @param LSBsToUse     Number of least significant bits to use in each color channel. Using more bits will result
	 *                      in a greater visual deviation from the original. Range is 1-8
	 */
	public static void encode(@NotNull String origMediaPath, @NotNull String fileToEncode, @NotNull String outMediaName, int LSBsToUse) {
		byte[] dataBytes;
		try {
			Spinner.printWithSpinner("Loading file to encode... ");
			dataBytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
			System.err.println("Error loading file to be hidden");
			return;
		}

		double origByteSize = dataBytes.length;
		dataBytes = Compressor.compress(dataBytes);

		boolean encrypted = Crypto.offerToCrypt(true);

		// Prepare data for use as part of AAD if encryption was used, and to be encoded into the image
		byte[] uncompFileSizeBits = BitByteConv.intToBitArray((int) origByteSize, SIZE_BITS_COUNT);

		// Compressed size of the data, accounting for the InitVector and AAD data bits, if encryption is to be used
		int compSize = dataBytes.length + (encrypted ? Crypto.AES_IV_SIZE + Crypto.GCM_AAD_SIZE / Byte.SIZE : 0);
		byte[] compFileSizeBits = BitByteConv.intToBitArray(compSize, SIZE_BITS_COUNT);

		Encoder encoder = getEncoder(origMediaPath, LSBsToUse);
		if (encoder == null)
			return;

		if (!encoder.doesFileFit(dataBytes.length * Byte.SIZE, LSBsToUse, encrypted))
			return;

		byte[] saltBytes = null;
		if (encrypted) {
			// Encrypt the data, and use the uncompressed and compressed data bits as AAD
			byte[][] saltAndDataBits = Crypto.encrypt(dataBytes, Crypto.genAAD(uncompFileSizeBits, compFileSizeBits));
			saltBytes = saltAndDataBits[0]; // Salt bytes, which are the last part of the header to be encoded
			dataBytes = saltAndDataBits[1]; // Encrypted data, with IV and AAD
		}

		Spinner.printWithSpinner("Encoding metadata... ");
		encoder.encodeBits(compFileSizeBits);
		encoder.encodeBits(uncompFileSizeBits);

		if (encrypted)
			encoder.encodeBytes(saltBytes);

		Spinner.printWithSpinner("Encoding data to media file... ");
		encoder.encodeBytes(dataBytes);
		encoder.stopThreads();

		if (encoder instanceof ImgEncoder)
			ImageProcessor.writeEncodedImageToDisk(((ImgEncoder) encoder).getImg(), outMediaName);
		else if (encoder instanceof AudioEncoder)
			AudioProcessor.writePCMToDisk(outMediaName, (AudioEncoder) encoder);

		System.out.println("Done!\n");
	}
}
