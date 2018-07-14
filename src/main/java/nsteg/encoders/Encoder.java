package nsteg.encoders;

import nsteg.Spinner;
import nsteg.encoders.aud.AudioEncoder;
import nsteg.processors.ImageProcessor;
import nsteg.encoders.img.EncoderThread;
import nsteg.encoders.img.ImgEncoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Compressor;
import nsteg.nsteg_utils.Crypto;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public abstract class Encoder {
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
		BufferedImage img;
		AudioInputStream raw;

		Spinner.printWithSpinner("Loading media file to encode file into... ");

		try {
			img = ImageIO.read(new URL(file));
			return new ImgEncoder(img, LSBsToUse);
		} catch (IOException ignored) {
		}

		try {
			raw = AudioSystem.getAudioInputStream(new File(file));
			AudioInputStream decoded = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, raw);
			return new AudioEncoder(decoded, LSBsToUse);
		} catch (UnsupportedAudioFileException | IOException ignored) {
		}

		System.err.println("File format not supported.");
		return null;
	}

	/**
	 * Encodes a file into an image, using the least significant bit(s) in the (A)RGB channels of each pixel to store
	 * the data. Optionally encrypts the data in the file using the AES-128 cipher and a hashed version of the key
	 * provided, which is hashed with Scrypt, in order to stave off brute-force attacks against the key.
	 *
	 * @param origImgPath    Path and name to image into which to encode the data
	 * @param fileToEncode   File to be encoded into the image
	 * @param outImgName     Path and name of the desired output image (copy of the original + file data)
	 * @param bitsPerChannel Number of least significant bits to use in each color channel. Using more bits will result
	 *                       in a greater visual deviation from the original. Range is 1-8
	 */
	public static void encode(String origImgPath, String fileToEncode, String outImgName, int bitsPerChannel) {
		byte[] dataBytes;
		try {
			Spinner.printWithSpinner("Loading file to encode... ");
			dataBytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
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

		Encoder encoder = getEncoder(origImgPath, bitsPerChannel);
		if (encoder == null)
			return;

		if (!encoder.doesFileFit(dataBytes.length * Byte.SIZE, bitsPerChannel, encrypted))
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

		Spinner.printWithSpinner("Encoding data to image... ");
		encoder.encodeBytes(dataBytes);
		encoder.stopThreads();

		if (encoder instanceof ImgEncoder)
			ImageProcessor.writeEncodedImageToDisk(((ImgEncoder) encoder).getImg(), outImgName);
		else if (encoder instanceof AudioEncoder)
			System.out.println("Write to disk");
	}
}
