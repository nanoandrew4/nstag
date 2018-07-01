package nstag.img;

import nstag.nStag;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * This class serves to decode data from an image that was previously encoded using the ImgEncoder class. It reads
 * from the least significant bits from each channel, in each pixel, in the image. The number of LSBs is retrieved from
 * the beginning of the image, where the number is encoded using only 1 LSB. See nStagImg for the specification of how
 * the data is encoded.
 *
 * The data is decoded sequentially, meaning that it must be decoded in the same order it was encoded, since that is
 * the way it was initially encoded by ImgEncoder.
 *
 * @see nStagImg
 */
public class ImgDecoder {
	private BufferedImage img; // Image to read (A)RGB data from and to write (A)ARGB modified data to
	private int x = 0, y = 0; // Pixel coords
	private int width, height; // Img dims

	private int bitsPerChannel = 1; // Number of LSBs to use in each channel for encoding purposes
	private final int numOfChannels;

	// Bits read from pixels are loaded to the buffer, for temporary storage, until the requested amount of them have been read
	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	ImgDecoder(BufferedImage encImg) {
		img = encImg;
		numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;
		width = img.getWidth();
		height = img.getHeight();

		/*
		 * Read the number of LSBs that were used when encoding data to this image. Values are read from the first pixel
		 * if the image is of ARGB type, or the first two pixels, if the image is of type RGB, since this value is
		 * encoded using only one LSB.
		 */
		bitsPerChannel = nStag.bitArrayToInt(readBits(4), false);
		buffer.clear();
	}

	public int getBitsPerChannel() {
		return bitsPerChannel;
	}

	/**
	 * Decodes bits from the image, until there are enough in the buffer to return the requested number of bits.
	 *
	 * Reads one pixel at a time, and adds all the decoded bits to the buffer, until the buffer has enough bits
	 * to return them. Any excess bits read will stay in the buffer, as they may belong to the next block of bits, if
	 * there is a next block.
	 *
	 * @param bitsToRead Desired number of bits to decode and return
	 * @return Array of bits with requested number of bits
	 */
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

	/**
	 * Reads bytes from the image, and returns them as a byte array. Uses readBits(byte[] bitsToRead) internally.
	 *
	 * @param bytesToRead Number of bytes to decode from the image
	 * @return Array of decoded bytes
	 */
	protected byte[] readBytes(int bytesToRead) {
		byte[] extractedBytes = new byte[bytesToRead];

		for (int i = 0; i < bytesToRead; i++)
			extractedBytes[i] = (byte) nStag.bitArrayToInt(readBits(8), true);
		return extractedBytes;
	}

	/**
	 * Loads a specified number of bits from the buffer into an array.
	 *
	 * @param bitsToRead Number of bits to load from the buffer to the array
	 * @return Array of bits of specified dimensions
	 */
	private byte[] loadFromBuffer(int bitsToRead) {
		byte[] bits = new byte[bitsToRead];
		for (int i = 0; i < bitsToRead; i++)
			bits[i] = buffer.pop();

		return bits;
	}

	/**
	 * Retrieves bits from the file that was encoded in the image by reading and storing the least significant bit(s)
	 * of each channel from the pixel (A)RGB value passed. The LSBs read are stored in the buffer, and once the buffer
	 * contains enough, an array of bits is returned with the requested number of them in the array.
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
