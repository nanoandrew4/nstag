package nsteg.img;

import nsteg.Spinner;
import nsteg.img.decoder.ImgDecoder;
import nsteg.img.encoder.ImgEncoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Compressor;
import nsteg.nsteg_utils.Crypto;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Controls the encoding and decoding of data to and from images.
 * <p><br>
 * Currently, this is how data is written to the images:
 * [# of bits per channel used] - [Compressed file size] - [Uncompressed file size] -
 * [Salt password was hashed with, if encryption was used] - [File data (encrypted or unencrypted)]
 * <p><br>
 * The header comprises everything that is not the data bits.
 * <p><br>
 * The number of bits per channel used is encoded and decoded using only the least significant bit, and is handled
 * by the ImgEncoder and ImgDecoder internally. All other blocks of data must be read or written in this class,
 * in order to properly encode/decode the data.
 */
public class nStegImg {

	// Numbver of bits used to encode/decode the size (compressed or uncompressed) of the data
	private final static int SIZE_BITS_COUNT = 4 * Byte.SIZE;

	// Number of bits used to encode/decode how many LSBs will go in each channel
	private final static int BITS_PER_CHANNEL_BIT_SIZE = 4;

	private static boolean dataFitsInImage(int fileSizeInBits, int maxBitsInImage, boolean encrypted) {
		long requiredBits = BITS_PER_CHANNEL_BIT_SIZE + (2 * SIZE_BITS_COUNT) + fileSizeInBits;
		if (encrypted)
			requiredBits += Crypto.GCM_AAD_SIZE + Crypto.AES_IV_SIZE + Crypto.SALT_SIZE_BITS;

		if (requiredBits > maxBitsInImage) {
			System.err.println("Not enough space in image, consider allowing more bits or using a larger image");
			System.err.println("Required capacity: " + requiredBits);
			System.err.println("Bits that can be encoded: " + maxBitsInImage);
			System.out.println();
			return false;
		}

		return true;
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
		BufferedImage origImg = null;
		byte[] dataBytes;
		try {
			Spinner.printWithSpinner("\nLoading image to encode to... ");
			origImg = ImageIO.read(new File(origImgPath));
			Spinner.printWithSpinner("Loading file to encode... ");
			dataBytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
			if (origImg == null)
				System.err.println("Image could not be loaded... Check you entered the right pathname");
			else
				System.err.println("File to encode could not be loaded... Check you entered the right pathname");
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

		if (!dataFitsInImage(compSize * Byte.SIZE,
				origImg.getWidth() * origImg.getHeight() * bitsPerChannel * (origImg.getColorModel().hasAlpha() ? 4 : 3),
				encrypted))
			return;

		byte[] saltBytes = null;
		if (encrypted) {
			// Encrypt the data, and use the uncompressed and compressed data bits as AAD
			byte[][] saltAndDataBits = Crypto.encrypt(dataBytes, Crypto.genAAD(uncompFileSizeBits, compFileSizeBits));
			saltBytes = saltAndDataBits[0]; // Salt bytes, which are the last part of the header to be encoded
			dataBytes = saltAndDataBits[1]; // Encrypted data, with IV and AAD
		}

		ImgEncoder ie = new ImgEncoder(origImg, bitsPerChannel);

		Spinner.printWithSpinner("Encoding metadata... ");
		ie.encodeBits(compFileSizeBits);
		ie.encodeBits(uncompFileSizeBits);

		if (encrypted)
			ie.encodeBytes(saltBytes);

		Spinner.printWithSpinner("Encoding data to image... ");
		ie.encodeBytes(dataBytes);
		ie.stopThreads();

		try {
			Spinner.printWithSpinner("Writing encoded data to disk... ");
			ImageIO.write(ie.getImg(), "png", new File(outImgName));
			Spinner.end();

			System.out.println("\nData encoded successfully into image: \"" + outImgName + "\"");
			System.out.println("Done!\n");
		} catch (IOException e) {
			System.err.println("Writing image to disk failed");
		}
	}

	/**
	 * Decodes a file from an image that was encoded using this program. If file requires decryption, it also allows the
	 * user to decrypt the data provided the data has not been tampered with.
	 *
	 * @param encodedPath Path and name of the image containing the encoded file
	 * @param outFileName Path and name under which to save the encoded file (include file name extension)
	 */
	public static void decode(String encodedPath, String outFileName) {
		boolean encrypted = Crypto.offerToCrypt(false);

		BufferedImage encoded;
		try {
			Spinner.printWithSpinner("Loading image to decode... ");
			encoded = ImageIO.read(new File(encodedPath));
		} catch (IOException e) {
			System.err.println("Input file could not be found, please input the correct path, name and file extension");
			return;
		}

		ImgDecoder id = new ImgDecoder(encoded);

		Spinner.printWithSpinner("Extracting metadata from image... ");
		byte[] compFileSizeBits = id.readBits(SIZE_BITS_COUNT);
		byte[] uncompFileSizeBits = id.readBits(SIZE_BITS_COUNT);

		int compFileSize = BitByteConv.bitArrayToInt(compFileSizeBits, false);
		int uncompFileSize = BitByteConv.bitArrayToInt(uncompFileSizeBits, false);

		byte[] saltBytes = null;
		if (encrypted)
			saltBytes = id.readBytes(Crypto.SALT_SIZE_BITS / Byte.SIZE);

		Spinner.printWithSpinner("Extracting file data from image... ");
		byte[] dataBytes = id.readBytes(compFileSize);

		Spinner.spin();
		if (encrypted)
			dataBytes = Crypto.decrypt(dataBytes, saltBytes, Crypto.genAAD(uncompFileSizeBits, compFileSizeBits));

		dataBytes = Compressor.decompress(dataBytes, uncompFileSize);

		try {
			Spinner.printWithSpinner("Writing decoded data to disk... ");
			FileOutputStream fos = new FileOutputStream(outFileName);
			fos.write(dataBytes);
			fos.close();

			Spinner.end();
			System.out.println("Data written successfully into file: \"" + outFileName + "\"");
			System.out.println("Done!\n");
		} catch (IOException e) {
			System.err.println("Could not write data to disk");
		}
	}
}
