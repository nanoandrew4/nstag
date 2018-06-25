package nstag;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;

public class nStagImg extends nStag {

	private final static int BITS_PER_CHANNEL_BITS = 4; // Nibble

	/**
	 * Encodes a file into an image, using the least significant bit(s) in the ARGB channels of each pixel to store the
	 * data. Optionally encrypts the data in the file using the AES128 cipher.
	 *
	 * @param origPath       Path and name to image into which to encode the data
	 * @param fileToEncode   File to be encoded into the image
	 * @param outName        Path and name of the desired output image (copy of the original + file data)
	 * @param bitsPerChannel Number of least significant bits to use in each color channel. Using more bits will result
	 *                       in a greater visual deviation from the original. Range is 1-8
	 */
	public static void encode(String origPath, String fileToEncode, String outName, int bitsPerChannel) {
		boolean encrypt = false;
		System.out.println("Do you wish to encrypt the data? Y/N: ");
		if ("y".equalsIgnoreCase(in.nextLine()))
			encrypt = true;

		byte[] dataBits, fileBytes;
		try {
			Spinner.printWithSpinner("Loading file to encode... ");
			fileBytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
			System.err.println("Input files could not be found, please input the correct path, name and extension");
			return;
		}

		byte[][] keyAndDataBits;
		byte[] keyBits = null;

		if (encrypt) {
			Spinner.printWithSpinner("Encrypting data... ");
			keyAndDataBits = encrypt(fileBytes); // Contains key bits and encrypted data byte array, respectively
			keyBits = keyAndDataBits[0]; // Key bits, encoded after file size and bits per channel, for later decryption

			Spinner.printWithSpinner("Converting encrypted data... ");
			dataBits = byteToBitArray(keyAndDataBits[1]);
		} else {
			Spinner.printWithSpinner("Converting file data... ");
			dataBits = byteToBitArray(fileBytes);
		}

		Spinner.printWithSpinner("Converting metadata values...");
		byte[] metadataBits = new byte[SIZE_BITS_COUNT + BITS_PER_CHANNEL_BITS + (encrypt ? KEY_BITS_COUNT : 0)];
		if (encrypt)
			System.arraycopy(
					keyBits, 0, metadataBits, SIZE_BITS_COUNT + BITS_PER_CHANNEL_BITS, keyBits.length
			);

		// Bits representing the file size (encrypted or otherwise, depending on what the user chooses)
		int[] fileSizeBitsArr = intToBitArray(dataBits.length, SIZE_BITS_COUNT, false); // File size bits
		for (int b = 0; b < SIZE_BITS_COUNT; b++)
			metadataBits[b] = (byte) fileSizeBitsArr[b];

		// Bits representing the number of bits that are used in each channel, in each pixel, to encode
		int[] bitsUsedArr = intToBitArray(bitsPerChannel, BITS_PER_CHANNEL_BITS, false); // Bits per channel
		for (int b = 0; b < BITS_PER_CHANNEL_BITS; b++)
			metadataBits[SIZE_BITS_COUNT + b] = (byte) bitsUsedArr[b];

		BufferedImage original;
		try {
			System.out.println();
			Spinner.printWithSpinner("Loading image to encode to... ");
			original = ImageIO.read(new File(origPath));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		/*
		 * If number of bits to encode is larger than number of bits that can be encoded in image, notify user and
		 * return. The image can hold: (pixels * 4 * (bits per channel)). Each pixel has 4 channels, alpha, red, green
		 * and blue.
		 */
		if (metadataBits.length + dataBits.length > original.getWidth() * original.getHeight() * bitsPerChannel * 4) {
			System.err.println("Not enough space in image, consider allowing more bits");
			System.err.println("Required bits: " + (metadataBits.length + dataBits.length));
			System.err.println("Available bits: " + (original.getWidth() * original.getHeight() * bitsPerChannel * 4));
			return;
		}

		BufferedImage out = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);

		Spinner.printWithSpinner("Encoding data to image... ");

		/*
		 * Write file bits to least significant bits of original image, until all have been written. Then just copy
		 * pixels from the original image until done. TODO: REWRITE
		 */
		int bitPos = 0;
		for (int j = 0; j < original.getHeight(); j++)
			for (int i = 0; i < original.getWidth(); i++) {
				if (bitPos < metadataBits.length) {
					out.setRGB(i, j, insertDataToPixel(original.getRGB(i, j), bitsPerChannel, metadataBits, bitPos));
					bitPos += 4 * bitsPerChannel;
				} else if (bitPos < metadataBits.length + dataBits.length) {
					out.setRGB(i, j, insertDataToPixel(original.getRGB(i, j), bitsPerChannel, dataBits, bitPos - metadataBits.length));
					bitPos += 4 * bitsPerChannel;
				} else
					out.setRGB(i, j, original.getRGB(i, j));
			}

		try {
			Spinner.printWithSpinner("Writing encoded image to disk... ");
			ImageIO.write(out, "png", new File(outName));
			Spinner.spin();
			System.out.println("\nData encoded successfully into image: \"" + outName + "\"\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Inserts bits from the file to be encoded into the least significant bit(s) of this pixel's channels, and returns
	 * the 32-bit integer representing the modified color that can be used with the BufferedImage.
	 *
	 * @param orig      Original 32-bit argb int representing the color of the pixel
	 * @param bitsToUse Number of least significant bits to use in each channel
	 * @param bits      ArrayDeque of bits that make up the file that is to be encoded
	 * @return Modified 32-bit argb int representing the new color of the pixel
	 */
	private static int insertDataToPixel(int orig, int bitsToUse, byte[] bits, int bitPos) {
		int[] aBits = intToBitArray(((orig >> 24) & 0xff), 8, false); // Get alpha bits
		int[] rBits = intToBitArray(((orig >> 16) & 0xff), 8, false); // Get red bits
		int[] gBits = intToBitArray(((orig >> 8) & 0xff), 8, false); // Get green bits
		int[] bBits = intToBitArray((orig & 0xff), 8, false); // Get blue bits

		// Write file bits to least significant bits of each channel (last array position)
		for (int bit = 0; bit < bitsToUse && bitPos + 4 * bitsToUse <= bits.length; bit++) {
			aBits[7 - bit] = bits[bitPos + bit * 4];
			rBits[7 - bit] = bits[bitPos + 1 + bit * 4];
			gBits[7 - bit] = bits[bitPos + 2 + bit * 4];
			bBits[7 - bit] = bits[bitPos + 3 + bit * 4];
		}

		// Return 32-bit int representing the color of the pixel, with some relevant bits stored in it
		return (((int) bitArrayToLong(aBits, false)) << 24) | ((int) (bitArrayToLong(rBits, false)) << 16)
				| ((int) (bitArrayToLong(gBits, false)) << 8) | ((int) bitArrayToLong(bBits, false));
	}

	/**
	 * Decodes a file from an image that was encoded using this program. If file requires decryption, it also allows the
	 * user to decrypt the data provided the data has not been tampered with.
	 *
	 * @param encodedPath Path and name of the image containing the encoded file
	 * @param outFileName Path and name under which to save the encoded file (include file name extension)
	 */
	public static void decode(String encodedPath, String outFileName) {
		boolean encrypted = false;
		System.out.println("Is this data encrypted? Y/N: ");
		if ("y".equalsIgnoreCase(in.nextLine()))
			encrypted = true;

		BufferedImage encoded;
		try {
			Spinner.printWithSpinner("Loading image to decode... ");
			encoded = ImageIO.read(new File(encodedPath));
		} catch (IOException e) {
			System.err.println("Input file could not be found, please input the correct path, name and file extension");
			return;
		}

		Spinner.printWithSpinner("Extracting data from image... ");

		/*
		 * Decodes size of file stored in the image from the first 8 pixels of the image. Each pixel has 4 channels,
		 * for a total of 32 bits, or 4 bytes that represent the size of the file encoded.
		 *
		 * File size is read from the least significant bits (last bit) of each channel value. Since each channel
		 * has range 0-255, it can be represented as 8 bits, and only the 8th is relevant.
		 */
		int[] imgSize = new int[SIZE_BITS_COUNT];
		for (int i = 0; i < SIZE_BITS_COUNT / 4; i++) {
			int p = encoded.getRGB(i, 0);
			imgSize[i * 4] = intToBitArray(((p >> 24) & 0xff), 8, false)[7];
			imgSize[i * 4 + 1] = intToBitArray(((p >> 16) & 0xff), 8, false)[7];
			imgSize[i * 4 + 2] = intToBitArray(((p >> 8) & 0xff), 8, false)[7];
			imgSize[i * 4 + 3] = intToBitArray((p & 0xff), 8, false)[7];
		}
		int bitsInImage = (int) bitArrayToLong(imgSize, false); // File size in bits

		// Decodes number of bits that are stored in each channel. This value is four bits in size (or a nibble)
		int[] bitsEncoded = new int[BITS_PER_CHANNEL_BITS];
		int p = encoded.getRGB(8, 0); // TODO: VANQUISH MAGIC NUMBERS
		bitsEncoded[0] = intToBitArray(((p >> 24) & 0xff), 8, false)[7];
		bitsEncoded[1] = intToBitArray(((p >> 16) & 0xff), 8, false)[7];
		bitsEncoded[2] = intToBitArray(((p >> 8) & 0xff), 8, false)[7];
		bitsEncoded[3] = intToBitArray((p & 0xff), 8, false)[7];
		int bitsPerPixel = (int) bitArrayToLong(bitsEncoded, false);

		/*
		 * Decodes all bits from image, until file is fully recovered. Because the first 9 pixels store encoding data,
		 * they are skipped on the first row of pixels of the image. In the event the data in encrypted, the program
		 * will look at the 92 bytes following the encoding data (file size and bitsPerChannel) for the key the data
		 * was encrypted with.
		 */
		int pxWidth = encoded.getWidth(), pxHeight = encoded.getHeight();
		ArrayDeque<Integer> bits = new ArrayDeque<>();
		for (int j = 0; j < pxHeight; j++)
			for (int i = j == 0 ? 9 : 0; i < pxWidth && (i + j * pxWidth - 9) * 4 < bitsInImage + (encrypted ? KEY_BITS_COUNT : 0); i++)
				extractDataFromPixel(encoded.getRGB(i, j), bitsPerPixel, bits);

		System.out.println();
		Spinner.printWithSpinner("Converting key... ");

		byte[] keyBytes = null;
		if (encrypted) {
			keyBytes = new byte[KEY_BITS_COUNT / 8]; // Container for key, which is 92 bytes in length
			// Recovers key from encoded bits
			for (int b = 0; b < KEY_BITS_COUNT / 8; b++) {
				int[] keyBits = {bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop()};
				keyBytes[b] = (byte) bitArrayToLong(keyBits, true);
			}
		}

		Spinner.printWithSpinner("Converting data... ");

		/*
		 * Creates a byte array in which to store the file bits decoded from the image, and recovers the data from the
		 * remaining bits that were extracted from the image.
		 */
		byte[] decodedBytes = new byte[bits.size() / 8];
		for (int b = 0; b < decodedBytes.length; b++) {
			int[] byteBits = {bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop()};
			decodedBytes[b] = (byte) bitArrayToLong(byteBits, true);
		}

		if (encrypted) {
			Spinner.printWithSpinner("Decrypting data... ");
			decodedBytes = decrypt(decodedBytes, keyBytes);
		}

		try {
			Spinner.printWithSpinner("Writing decoded data to disk... ");
			FileOutputStream fos = new FileOutputStream(outFileName);
			fos.write(decodedBytes);
			fos.close();
			Spinner.spin();
			System.out.println("Data decoded successfully into file: \"" + outFileName + "\"\n\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Retrieves bits from the file that was encoded in the image by reading and storing the least significant bit(s)
	 * of each channel in this pixel.
	 *
	 * @param orig      32-bit argb int representing the colors the values of the 4 color channels
	 * @param bitsToUse Number of least significant bit(s) to use in each channel
	 * @param bits      ArrayDeque of bits to which to write the decoded data from this pixel
	 */
	private static void extractDataFromPixel(int orig, int bitsToUse, ArrayDeque<Integer> bits) {
		int[] aBits = intToBitArray(((orig >> 24) & 0xff), 8, false); // Get alpha channel value
		int[] rBits = intToBitArray(((orig >> 16) & 0xff), 8, false); // Get red channel value
		int[] gBits = intToBitArray(((orig >> 8) & 0xff), 8, false); // Get green channel value
		int[] bBits = intToBitArray((orig & 0xff), 8, false); // Get blue channel value

		// Write least significant bit(s) from the color channels to the queue of bits to recover the encoded file
		for (int bit = 0; bit < bitsToUse; bit++) {
			bits.add(aBits[7 - bit]);
			bits.add(rBits[7 - bit]);
			bits.add(gBits[7 - bit]);
			bits.add(bBits[7 - bit]);
		}
	}
}
