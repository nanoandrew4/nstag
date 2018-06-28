package nstag;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

public class ImgEncoder {
	private BufferedImage img;
	private int x = 0, y = 0;
	private int width, height;

	public static boolean deb = true;

	private int bitsPerChannel = 1, nextChanToWrite = 0, numOfChannels;
	private int currLSB = 0;

	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	// Seq writes
	ImgEncoder(BufferedImage origImg, int bitsPerChannel) {
		img = origImg;
		numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;
		width = img.getWidth();
		height = img.getHeight();

		encodeBits(nStag.intToBitArray(bitsPerChannel, 4, false));
		if (numOfChannels == 3)
			x = 2;
		currLSB = nextChanToWrite = 0;

		this.bitsPerChannel = bitsPerChannel;
	}

	public BufferedImage getImg() {
		return img;
	}

	public void encodeBits(byte[] bitsToEncode) {
		int pos = 0;
		for (; y < height; y++) {
			for (; x < width; ) {
				if (pos >= bitsToEncode.length && buffer.isEmpty()) {
					return;
				}

				while (buffer.size() < numOfChannels * bitsPerChannel && pos < bitsToEncode.length)
					buffer.add(bitsToEncode[pos++]);

				img.setRGB(x, y, insertDataToPixel(img.getRGB(x, y)));

				if ((currLSB %= bitsPerChannel) == 0)
					x++;
			}
			x = 0;
		}
	}

	public void encodeBytes(byte[] bytesToEncode) {
		for (byte b : bytesToEncode)
			encodeBits(nStag.intToBitArray(b, 8, true));
	}

	/**
	 * Inserts bits from the file to be encoded into the least significant bit(s) of this pixel's channels, and returns
	 * the 32-bit integer representing the modified color that can be used with the BufferedImage.
	 *
	 * @param orig Original 32-bit argb int representing the color of the pixel
	 * @return Modified 32-bit argb int representing the new color of the pixel
	 */
	private int insertDataToPixel(int orig) {
		byte[] aBits = nStag.intToBitArray(((orig >> 24) & 0xff), 8, false); // Get alpha bits
		byte[] rBits = nStag.intToBitArray(((orig >> 16) & 0xff), 8, false); // Get red bits
		byte[] gBits = nStag.intToBitArray(((orig >> 8) & 0xff), 8, false); // Get green bits
		byte[] bBits = nStag.intToBitArray((orig & 0xff), 8, false); // Get blue bits

		// Write file bits to least significant bits of each channel (last array position)
		for (; currLSB < bitsPerChannel && !buffer.isEmpty(); ) {
			rBits[7 - currLSB] = buffer.pop();

			if (!buffer.isEmpty()) {
				gBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 2;
			} else if (buffer.isEmpty())
				break;

			if (!buffer.isEmpty() && nextChanToWrite == 2) {
				bBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 3 % numOfChannels;
				if (numOfChannels == 3)
					currLSB++;
			} else if (buffer.isEmpty())
				break;

			if (nextChanToWrite == 3 && !buffer.isEmpty()) {
				aBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 0;
				currLSB++;
			}
		}

		// Return 32-bit int representing the color of the pixel, with some relevant bits stored in it
		return (nStag.bitArrayToInt(aBits, false) << 24) | (nStag.bitArrayToInt(rBits, false) << 16)
				| (nStag.bitArrayToInt(gBits, false)) << 8 | nStag.bitArrayToInt(bBits, false);
	}
}