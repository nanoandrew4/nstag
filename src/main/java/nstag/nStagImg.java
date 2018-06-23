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

	final static int BITS_PER_CHANNEL_BITS = 4; // Nibble

	/**
	 * Encodes a file into an image, using the least significant bit(s) in the channels of each pixel to store the data.
	 *
	 * @param origPath       Path and name to image into which to encode the data
	 * @param fileToEncode   File to be encoded into the image
	 * @param outName        Path and name of the desired output image (copy of the original + file data)
	 * @param bitsPerChannel Number of least significant bits to use in each color channel. Using more bits will result
	 *                       in a greater visual deviation from the original. Range is 1-8
	 */
	public static void encode(String origPath, String fileToEncode, String outName, int bitsPerChannel) {
		byte[] bitsToEncode;
		try {
			byte[] fileBytes = Files.readAllBytes(Paths.get(fileToEncode));

			/*
			 * If number of bits to encode is larger than number of bits that can be encoded in image, notify user and return.
			 * The image can hold: (pixels * 4 * (bits per channel)). Each pixel has 4 channels, alpha, red, green and blue.
			 */
//		if (bytes.length * 8 > original.getWidth() * original.getHeight() * bitsPerChannel * 4) {
//			System.err.println("Not enough space in image, consider allowing more bits");
//			System.err.println("Required bits: " + (bytes.length * 8));
//			System.err.println("Available bits: " + (original.getWidth() * original.getHeight() * bitsPerChannel * 4));
//			return;
//		}

			System.out.print("Encoding... ");
//			Spinner.spin();

			int fileBitSize = fileBytes.length * 8;

			bitsToEncode = new byte[fileBitSize + KEY_BITS_COUNT + SIZE_BITS_COUNT + BITS_PER_CHANNEL_BITS];

			// Queue of bits to be encoded in the image
			int[] bitsArr;
			int pos = 0;
			for (byte b : fileBytes) {
				bitsArr = intToBitArray(b, 8, true);
				for (int aBitsArr : bitsArr)
					bitsToEncode[KEY_BITS_COUNT + SIZE_BITS_COUNT + BITS_PER_CHANNEL_BITS + pos++] = ((byte) aBitsArr);
			}
		} catch (IOException e) {
			System.err.println("Input files could not be found, please input the correct path, name and extension");
			return;
		}

//		Spinner.spin();
		bitsToEncode = offerToEncrypt(bitsToEncode, KEY_BITS_COUNT + SIZE_BITS_COUNT + BITS_PER_CHANNEL_BITS);
		if (bitsToEncode == null) {
			System.err.println("Error during encryption... Aborting...");
			return;
		}

		// Queue of bits defining the size of the encoded file, and the number of bits taken in each channel
		int[] fileSizeBitsArr = intToBitArray(bitsToEncode.length, SIZE_BITS_COUNT, false); // File size bits
		for (int b = 0; b < SIZE_BITS_COUNT; b++)
			bitsToEncode[b] = (byte) fileSizeBitsArr[b];

		int[] bitsUsedArr = intToBitArray(bitsPerChannel, BITS_PER_CHANNEL_BITS, false); // Bits per channel
		for (int b = 0; b < BITS_PER_CHANNEL_BITS; b++)
			bitsToEncode[SIZE_BITS_COUNT + b] = (byte) bitsUsedArr[b];

		// Write data to fileBits
		BufferedImage original;
		try {
			original = ImageIO.read(new File(origPath));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		BufferedImage out = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);

		/*
		 * Write file bits to least significant bits of original image, until all have been written. Then just copy
		 * pixels from the original image until done.
		 */

		int bitPos = 0;
		for (int j = 0; j < original.getHeight(); j++)
			for (int i = 0; i < original.getWidth(); i++) {
				if (bitPos < bitsToEncode.length) {
					out.setRGB(i, j, insertDataToPixel(original.getRGB(i, j), bitsPerChannel, bitsToEncode, bitPos));
					bitPos += 4 * bitsPerChannel;
				}
				else
					out.setRGB(i, j, original.getRGB(i, j));
			}

		try {
			ImageIO.write(out, "png", new File(outName));
//			Spinner.spin();
			System.out.println("Data encoded successfully into image: \"" + outName + "\"\n\n");
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
		for (int bit = 0; bit < bitsToUse && bitPos + 4 * bitsToUse < bits.length; bit++) {
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
	 * Decodes a file from an image that was encoded using this program.
	 *
	 * @param encodedPath Path and name of the image containing the encoded file
	 * @param outFileName Path and name under which to save the encoded file (include file name extension)
	 */
	public static void decode(String encodedPath, String outFileName) {
		BufferedImage encoded;
		try {
			encoded = ImageIO.read(new File(encodedPath));
		} catch (IOException e) {
			System.err.println("Input file could not be found, please input the correct path, name and extension");
			return;
		}

		System.out.print("Decoding... ");
		Spinner.spin();

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

		// Decodes number of bits that are stored in each channel, which is 4 bits in size
		int[] bitsEncoded = new int[BITS_PER_CHANNEL_BITS];
		int p = encoded.getRGB(8, 0); // TODO: VANQUISH MAGIC NUMBERS
		bitsEncoded[0] = intToBitArray(((p >> 24) & 0xff), 8, false)[7];
		bitsEncoded[1] = intToBitArray(((p >> 16) & 0xff), 8, false)[7];
		bitsEncoded[2] = intToBitArray(((p >> 8) & 0xff), 8, false)[7];
		bitsEncoded[3] = intToBitArray((p & 0xff), 8, false)[7];
		int bitsPerPixel = (int) bitArrayToLong(bitsEncoded, false);

//		int[] keySizeBits = new int[KEY_SIZE_BITS];
//		for (int i = 0; i < KEY_SIZE_BITS / 8; i++) {
//			p = encoded.getRGB(9 + i, 0);
//			keySizeBits[i * 4] = intToBitArray(((p >> 24) & 0xff), 8, false)[7];
//			keySizeBits[i * 4 + 1] = intToBitArray(((p >> 16) & 0xff), 8, false)[7];
//			keySizeBits[i * 4 + 2] = intToBitArray(((p >> 8) & 0xff), 8, false)[7];
//			keySizeBits[i * 4 + 3] = intToBitArray((p & 0xff), 8, false)[7];
//		}
//		int keySize = (int) bitArrayToLong(keySizeBits, false);

		/*
		 * Decodes all bits from image, until file is fully recovered. Because the first 9 pixels store encoding data,
		 * they are skipped on the first row of pixels of the image.
		 */
		int pxWidth = encoded.getWidth(), pxHeight = encoded.getHeight();
		ArrayDeque<Integer> bits = new ArrayDeque<>();
		for (int j = 0; j < pxHeight; j++)
			for (int i = j == 0 ? 9 : 0; i < pxWidth && (i + j * pxWidth - 9) * 4 < bitsInImage; i++)
				extractDataFromPixel(encoded.getRGB(i, j), bitsPerPixel, bits);

		byte[] keyBits = new byte[KEY_BITS_COUNT / 8];
		for (int b = 0; b < KEY_BITS_COUNT / 8; b++) {
			int[] byteBits = {bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop()};
			keyBits[b] = (byte) bitArrayToLong(byteBits, true);
		}

		/*
		 * Creates a byte array in which to store all the bits decoded from the image, and writes the bytes of the
		 * encoded file to the array.
		 */

		byte[] decodedBytes = new byte[bits.size() / 8];
		for (int b = 0; b < decodedBytes.length; b++) {
			int[] byteBits = {bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop()};
			decodedBytes[b] = (byte) bitArrayToLong(byteBits, true);
		}

		Spinner.spin();
		decodedBytes = offerToDecrypt(decodedBytes, keyBits);
		if (decodedBytes == null)
			return;

		try {
			FileOutputStream fos = new FileOutputStream(outFileName);
			fos.write(decodedBytes);
			fos.close();
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
