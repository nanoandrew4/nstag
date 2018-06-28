package nstag;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

public class ImgDecoder {
	private BufferedImage img;
	private int x = 0, y = 0, currLSB = 0;
	private int width, height;

	private int bitsPerChannel = 1;
	private final int numOfChannels;

	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	// Seq reads
	ImgDecoder(BufferedImage encImg) {
		img = encImg;
		numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;
		width = img.getWidth();
		height = img.getHeight();

		bitsPerChannel = nStag.bitArrayToInt(readBits(4), false);
		buffer.clear();
		System.out.println("BPC: " + bitsPerChannel);
	}

	protected byte[] readBits(int bitsToRead) {
		for (; y < height; y++) {
			for (; x < width;) {
				while (buffer.size() < bitsToRead && x < width)
					extractDataFromPixel(img.getRGB(x++, y));

				if (buffer.size() >= bitsToRead)
					return loadFromBuffer(bitsToRead);
			}
			x = 0;
		}

		System.err.println("No more bits to read...");
		return null;
	}

	protected byte[] readBytes(int bytesToRead) {
		byte[] extractedBytes = new byte[bytesToRead];

		for (int i = 0; i < bytesToRead; i++)
			extractedBytes[i] = (byte) nStag.bitArrayToInt(readBits(8), true);
		return extractedBytes;
	}

	private byte[] loadFromBuffer(int bitsToRead) {
		byte[] bits = new byte[bitsToRead];
		for (int i = 0; i < bitsToRead; i++)
			bits[i] = buffer.pop();

		return bits;
	}

	/**
	 * Retrieves bits from the file that was encoded in the image by reading and storing the least significant bit(s)
	 * of each channel in this pixel.
	 *
	 * @param orig 32-bit argb int representing the colors the values of the 4 color channels
	 */
	protected void extractDataFromPixel(int orig) {
		byte[] aBits = nStag.intToBitArray((orig >> 24) & 0xff, 8, false); // Get alpha channel value
		byte[] rBits = nStag.intToBitArray((orig >> 16) & 0xff, 8, false); // Get red channel value
		byte[] gBits = nStag.intToBitArray((orig >> 8) & 0xff, 8, false); // Get green channel value
		byte[] bBits = nStag.intToBitArray(orig & 0xff, 8, false); // Get blue channel value

		// Write least significant bit(s) from the color channels to the queue of bits to recover the encoded file
		for (int lsb = 0; lsb < bitsPerChannel; lsb++) {
			buffer.add(rBits[7 - lsb]);
			buffer.add(gBits[7 - lsb]);
			buffer.add(bBits[7 - lsb]);
			if (numOfChannels == 4)
				buffer.add(aBits[7 - lsb]);
		}
	}
}
