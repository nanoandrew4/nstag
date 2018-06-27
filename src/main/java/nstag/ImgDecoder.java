package nstag;

import java.awt.image.BufferedImage;

public class ImgDecoder {
	private BufferedImage img;
	private int x = 0, y = 0, currLSB = 0;
	private int width, height;

	private int bitsPerChannel = 1;
	private final int NUM_OF_CHANNELS = 4;

	ImgDecoder(BufferedImage encImg) {
		img = encImg;
		width = img.getWidth();
		height = img.getHeight();

		bitsPerChannel = nStag.bitArrayToInt(readBits(4), false);
	}

	protected byte[] readBits(int bitsToRead) {
		byte[] extractedBits = new byte[bitsToRead];
		int bitPos = 0;

		for (; y < height; y++) {
			for (; x < width;) {
				if (bitPos >= bitsToRead)
					return extractedBits;
				int bitsWritten = extractDataFromPixel(img.getRGB(x, y), extractedBits, bitPos);

				if (currLSB == 0)
					x++;

				if (bitsWritten == NUM_OF_CHANNELS && bitsWritten != bitsToRead)
					extractDataFromPixel(img.getRGB(x, y), extractedBits, bitsWritten);
				bitPos += bitsPerChannel * NUM_OF_CHANNELS;
			}
			x = 0;
		}

		return extractedBits;
	}

	protected byte[] readBytes(int bytesToRead) {
		byte[] extractedBytes = new byte[bytesToRead];

		for (int i = 0; i < bytesToRead; i++)
			extractedBytes[i] = (byte) nStag.bitArrayToInt(readBits(8), true);
		return extractedBytes;
	}

	/**
	 * Retrieves bits from the file that was encoded in the image by reading and storing the least significant bit(s)
	 * of each channel in this pixel.
	 *
	 * @param orig 32-bit argb int representing the colors the values of the 4 color channels
	 * @param bits Array of bits to which to write the decoded data from this pixel
	 */
	protected int extractDataFromPixel(int orig, byte[] bits, int bitPos) {
		byte[] aBits = nStag.intToBitArray((orig >> 24) & 0xff, 8, false); // Get alpha channel value
		byte[] rBits = nStag.intToBitArray((orig >> 16) & 0xff, 8, false); // Get red channel value
		byte[] gBits = nStag.intToBitArray((orig >> 8) & 0xff, 8, false); // Get green channel value
		byte[] bBits = nStag.intToBitArray(orig & 0xff, 8, false); // Get blue channel value

		int bitsWritten = 0;

		// Write least significant bit(s) from the color channels to the queue of bits to recover the encoded file
		for (; currLSB < bitsPerChannel && bitPos + (currLSB + 1) * NUM_OF_CHANNELS <= bits.length; currLSB++) {
			bits[bitPos + bitsWritten] = aBits[7 - currLSB];
			bits[bitPos + 1 + bitsWritten] = rBits[7 - currLSB];
			bits[bitPos + 2 + bitsWritten] = gBits[7 - currLSB];
			bits[bitPos + 3 + bitsWritten] = bBits[7 - currLSB];
			bitsWritten += NUM_OF_CHANNELS;
		}

		currLSB %= bitsPerChannel;
		return bitsWritten;
	}
}
