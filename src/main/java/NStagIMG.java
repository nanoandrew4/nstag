import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;

public class NStagIMG {

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
		byte[] bytes;
		BufferedImage original;
		try {
			original = ImageIO.read(new File(origPath));
			bytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
			System.err.println("Input files could not be found, please input the correct path, name and extension");
			return;
		}

		/*
		 * If number of bits to encode is larger than number of bits that can be encoded in image, notify user and return.
		 * The image can hold: (pixels * 4 * (bits per channel)). Each pixel has 4 channels, alpha, red, green and blue.
		 */
		if (bytes.length * 8 > original.getWidth() * original.getHeight() * bitsPerChannel * 4) {
			System.err.println("Not enough space in image, consider allowing more bits");
			System.err.println("Required bits: " + (bytes.length * 8));
			System.err.println("Available bits: " + (original.getWidth() * original.getHeight() * bitsPerChannel * 4));
			return;
		}

		System.out.print("Encoding... ");
		Spinner.spin();

		// Queue of bits to be encoded in the image
		ArrayDeque<Integer> bits = new ArrayDeque<>();
		int[] bitsArr;
		for (byte aByte : bytes) {
			bitsArr = getBits(aByte, 8, true);
			for (int aBitsArr : bitsArr) bits.add(aBitsArr);
		}

		BufferedImage out = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);

		// Queue of bits defining the size of the encoded file, and the number of bits taken in each channel
		ArrayDeque<Integer> encodingData = new ArrayDeque<>();
		int[] fileSizeBitsArr = getBits(bits.size(), 32, false); // File size bits
		for (int aFileSizeBitsArr : fileSizeBitsArr) encodingData.add(aFileSizeBitsArr);
		int[] bitsUsedArr = getBits(bitsPerChannel, 4, false); // BIts per channel bits
		for (int aBitsUsedArr : bitsUsedArr) encodingData.add(aBitsUsedArr);

		/*
		 * Encode file size and number of bits per channel into pixels. Each pixel has 4 channels, which means the
		 * number of bits that can be encoded into each pixel is 4 * (bits per channel)
		 */
		for (int b = 0; b < 9; b++)
			out.setRGB(b, 0, insertDataToPixel(original.getRGB(b, 0), 1, encodingData));

		/*
		 * Write file bits to least significant bits of original image, until all have been written. Then just copy
		 * pixels from the original image until done.
		 */
		for (int j = 0; j < original.getHeight(); j++)
			for (int i = j == 0 ? 9 : 0; i < original.getWidth(); i++) {
				if (bits.size() > 0)
					out.setRGB(i, j, insertDataToPixel(original.getRGB(i, j), bitsPerChannel, bits));
				else
					out.setRGB(i, j, original.getRGB(i, j));
			}

		try {
			ImageIO.write(out, "png", new File(outName));
			Spinner.spin();
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
	private static int insertDataToPixel(int orig, int bitsToUse, ArrayDeque<Integer> bits) {
		int[] aBits = getBits(((orig >> 24) & 0xff), 8, false); // Get alpha bits
		int[] rBits = getBits(((orig >> 16) & 0xff), 8, false); // Get red bits
		int[] gBits = getBits(((orig >> 8) & 0xff), 8, false); // Get green bits
		int[] bBits = getBits((orig & 0xff), 8, false); // Get blue bits

		// Write file bits to least significant bits of each channel (last array position)
		for (int bit = 0; bit < bitsToUse && !bits.isEmpty(); bit++) {
			aBits[7 - bit] = bits.pop();
			rBits[7 - bit] = bits.pop();
			gBits[7 - bit] = bits.pop();
			bBits[7 - bit] = bits.pop();
		}

		// Return 32-bit int representing the color of the pixel, with some relevant bits stored in it
		return (toByte(aBits, false) << 24) | (toByte(rBits, false) << 16)
				| (toByte(gBits, false) << 8) | toByte(bBits, false);
	}

	/**
	 * Converts a decimal number into an array of bits. Can handle both signed and unsigned binary numbers.
	 *
	 * @param b         Number to convert to array of bits
	 * @param numOfBits Number of bits in the number that is being converted
	 * @param signed    True for interpreting as a signed number, false for interpreting as an unsigned number
	 * @return Array of bits representing the decimal number passed as an argument, in signed or unsigned form
	 */
	private static int[] getBits(int b, int numOfBits, boolean signed) {
		int[] bits = new int[numOfBits];
		boolean neg = b < 0;
		if (signed && neg) {
			b = ~b; // NOT op, flips all the bits so it can be operated upon normally
			bits[0] = 1; // Set first bit to indicate the bits represent a negative number
		}

		/*
		 * Break number down into binary. If number is negative, all 0's should be 1's, and viceversa. That is
		 * achieved with the modulo operation, by adding one and mod 2, the bits will be opposite what they should be,
		 * which is the desired output when a number is negative.
		 */
		for (int i = 0; i < numOfBits; i++) {
			bits[numOfBits - 1 - i] = (b + (signed && neg ? 1 : 0)) % 2;
			b >>>= 1;
		}
		return bits;
	}

	/**
	 * Converts an array of integers (containing exclusively binary numbers) to a decimal representation of itself.
	 * Can handle conversion of both signed and unsigned binary numbers.
	 *
	 * @param bits   Array of bits to be converted to a decimal integer
	 * @param signed True for interpreting as a signed number, false to interpret as an unsigned number
	 * @return Integer representation of the array of bits
	 */
	private static int toByte(int[] bits, boolean signed) {
		boolean neg = signed && bits[0] == 1;
		int b = signed && neg ? -128 : 0;
		for (int i = (signed && neg ? 1 : 0); i < bits.length; i++)
			if (bits[i] != 0)
				b += Math.pow(2, bits.length - 1 - i);
		return b;
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
		int[] imgSize = new int[32];
		for (int i = 0; i < 8; i++) {
			int p = encoded.getRGB(i, 0);
			imgSize[i * 4] = getBits(((p >> 24) & 0xff), 8, false)[7];
			imgSize[i * 4 + 1] = getBits(((p >> 16) & 0xff), 8, false)[7];
			imgSize[i * 4 + 2] = getBits(((p >> 8) & 0xff), 8, false)[7];
			imgSize[i * 4 + 3] = getBits((p & 0xff), 8, false)[7];
		}
		int bitsInImage = toByte(imgSize, false); // File size in bits

		// Decodes number of bits that are stored in each channel
		int[] bitsEncoded = new int[4];
		int p = encoded.getRGB(8, 0);
		bitsEncoded[0] = getBits(((p >> 24) & 0xff), 8, false)[7];
		bitsEncoded[1] = getBits(((p >> 16) & 0xff), 8, false)[7];
		bitsEncoded[2] = getBits(((p >> 8) & 0xff), 8, false)[7];
		bitsEncoded[3] = getBits((p & 0xff), 8, false)[7];
		int bitsPerPixel = toByte(bitsEncoded, false);

		/*
		 * Decodes all bits from image, until file is fully recovered. Because the first 9 pixels store encoding data,
		 * they are skipped on the first row of pixels of the image.
		 */
		int pxWidth = encoded.getWidth(), pxHeight = encoded.getHeight();
		ArrayDeque<Integer> bits = new ArrayDeque<>();
		for (int j = 0; j < pxHeight; j++)
			for (int i = j == 0 ? 9 : 0; i < pxWidth && (i + j * pxWidth - 9) * 4 < bitsInImage; i++)
				extractDataFromPixel(encoded.getRGB(i, j), bitsPerPixel, bits);

		/*
		 * Creates a byte array in which to store all the bits decoded from the image, and writes the bytes of the
		 * encoded file to the array.
		 */
		byte[] decodedBytes = new byte[bits.size() / 8];
		for (int b = 0; b < decodedBytes.length; b++) {
			int[] byteBits = {bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop()};
			decodedBytes[b] = (byte) toByte(byteBits, true);
		}

		try {
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
		int[] aBits = getBits(((orig >> 24) & 0xff), 8, false); // Get alpha channel value
		int[] rBits = getBits(((orig >> 16) & 0xff), 8, false); // Get red channel value
		int[] gBits = getBits(((orig >> 8) & 0xff), 8, false); // Get green channel value
		int[] bBits = getBits((orig & 0xff), 8, false); // Get blue channel value

		// Write least significant bit(s) from the color channels to the queue of bits to recover the encoded file
		for (int bit = 0; bit < bitsToUse; bit++) {
			bits.add(aBits[7 - bit]);
			bits.add(rBits[7 - bit]);
			bits.add(gBits[7 - bit]);
			bits.add(bBits[7 - bit]);
		}
	}
}
