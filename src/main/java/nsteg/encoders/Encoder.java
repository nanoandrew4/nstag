package nsteg.encoders;

import nsteg.Spinner;
import nsteg.encoders.aud.AudioEncoder;
import nsteg.encoders.img.EncoderThread;
import nsteg.encoders.img.ImgEncoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Compressor;
import nsteg.nsteg_utils.Crypto;
import nsteg.processors.AudioProcessor;
import nsteg.processors.ImageProcessor;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;

public abstract class Encoder {
	public static LinkedList<String> inImgFormats = new LinkedList<>();
	public static LinkedList<String> outImgFormats = new LinkedList<>();

	public static LinkedList<String> inAudFormats = new LinkedList<>();
	public static LinkedList<String> outAudFormats = new LinkedList<>();

	static {
		String[] inImgSuffixes = ImageIO.getReaderFileSuffixes();
		inImgFormats.addAll(Arrays.asList(inImgSuffixes));
		inImgFormats.add("tif");
		inImgFormats.add("tiff");

		String[] outImgSuffixes = {"png", "bmp", "tif", "tiff"};
		outImgFormats.addAll(Arrays.asList(outImgSuffixes));

		String[] inAudSuffixes = {"mp3", "wav"};
		inAudFormats.addAll(Arrays.asList(inAudSuffixes));

		String[] outAudSuffixes = {"wav"};
		outAudFormats.addAll(Arrays.asList(outAudSuffixes));
	}

	protected EncoderThread[] encThreads = new EncoderThread[Runtime.getRuntime().availableProcessors()];

	// Number of bits used to encode/decode the size (compressed or uncompressed) of the data
	public final static int SIZE_BITS_COUNT = 4 * Byte.SIZE;

	public final static int LEAST_SIG_BITS_TO_USE = 4;

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

	public abstract void encodeBits(byte[] bits);

	public abstract void encodeBytes(byte[] bytes);

	public abstract boolean doesFileFit(int fileSizeInBits, int LSBsToUse, boolean encrypted);

	public abstract void stopThreads();

	private static Encoder getEncoder(String file, int LSBsToUse) {
		Spinner.printWithSpinner("Loading media file to encode file into... ");

		String[] split = file.split("\\.");
		String suffix = split[split.length - 1];

		Encoder encoder = null;

		if (inImgFormats.contains(suffix)) {
			try {
				encoder = new ImgEncoder(ImageIO.read(new URL(file)), LSBsToUse);
			} catch (IOException ignored) {
			}
		} else if (inAudFormats.contains(suffix)) {
			try {
				AudioInputStream decoded = AudioSystem.getAudioInputStream(
						AudioFormat.Encoding.PCM_SIGNED,
						AudioSystem.getAudioInputStream(new File(file))
				);
				encoder = new AudioEncoder(decoded, LSBsToUse);
			} catch (UnsupportedAudioFileException | IOException ignored) {
			}
		} else {
			System.err.println("File format not supported.");
		}

		Spinner.end();
		return encoder;
	}

	/**
	 * Encodes a file into an image, using the least significant bit(s) in the (A)RGB channels of each pixel to store
	 * the data. Optionally encrypts the data in the file using the AES-128 cipher and a hashed version of the key
	 * provided, which is hashed with Scrypt, in order to stave off brute-force attacks against the key.
	 *
	 * @param origMediaPath Path and name to image into which to encode the data
	 * @param fileToEncode  File to be encoded into the image
	 * @param outMediaName  Path and name of the desired output image (copy of the original + file data)
	 * @param LSBsToUse     Number of least significant bits to use in each color channel. Using more bits will result
	 *                      in a greater visual deviation from the original. Range is 1-8
	 */
	public static void encode(String origMediaPath, String fileToEncode, String outMediaName, int LSBsToUse) {
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
			AudioProcessor.writePCMToWAV(outMediaName, ((AudioEncoder) encoder).getEncodedPCM(),
					((AudioEncoder) encoder).getChannels(), ((AudioEncoder) encoder).getSampleRate()
			);

		System.out.println("Done!\n");
	}
}
