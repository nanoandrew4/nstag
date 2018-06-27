package nstag;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImgEncoder {
	private BufferedImage orig, enc;
	private int x = 0, y = 0;
	private int width, height;

	private int bitsPerChannel = 1;
	private final int NUM_OF_CHANNELS = 4;

	ImgEncoder(BufferedImage origImg, int bitsPerChannel) {
		orig = origImg;
		width = orig.getWidth();
		height = orig.getHeight();

		encodeBits(nStag.intToBitArray(bitsPerChannel, 4, false));

		this.bitsPerChannel = bitsPerChannel;
	}

	public BufferedImage getImg() {
		return orig;
	}

	public void encodeBits(byte[] bitsToEncode) {
		int pos = 0;
		for (; y < height; y++) {
			for (; x < width; x++) {
				if (pos >= bitsToEncode.length)
					return;

				orig.setRGB(x, y, insertDataToPixel(orig.getRGB(x, y), bitsToEncode, pos));
				pos += 4 * bitsPerChannel;
			}
			x = 0;
		}
	}

	public void encodeBytes(byte[] bytesToEncode, int startNibble) {
		byte[] byteBits = new byte[bitsPerChannel * NUM_OF_CHANNELS];
		int currNibble = startNibble;

		for (; y < height; y++) {
			for (; x < width; x++) {
				for (int i = 0; i < bitsPerChannel; i++) {
					if (currNibble / 2 >= bytesToEncode.length)
						return;

					byte[] bits = nStag.intToBitArray(bytesToEncode[currNibble / 2], 8, true);
					if (currNibble % 2 == 0)
						System.arraycopy(bits, 0, byteBits, i * 4, 4);
					else
						System.arraycopy(bits, 4, byteBits, i * 4, 4);
					currNibble++;
				}
				orig.setRGB(x, y, insertDataToPixel(orig.getRGB(x, y), byteBits, 0));
			}
			x = 0;
		}
	}

	/**
	 * Inserts bits from the file to be encoded into the least significant bit(s) of this pixel's channels, and returns
	 * the 32-bit integer representing the modified color that can be used with the BufferedImage.
	 *
	 * @param orig Original 32-bit argb int representing the color of the pixel
	 * @param bits ArrayDeque of bits that make up the file that is to be encoded
	 * @return Modified 32-bit argb int representing the new color of the pixel
	 */
	private int insertDataToPixel(int orig, byte[] bits, int bitPos) {
		byte[] aBits = nStag.intToBitArray(((orig >> 24) & 0xff), 8, false); // Get alpha bits
		byte[] rBits = nStag.intToBitArray(((orig >> 16) & 0xff), 8, false); // Get red bits
		byte[] gBits = nStag.intToBitArray(((orig >> 8) & 0xff), 8, false); // Get green bits
		byte[] bBits = nStag.intToBitArray((orig & 0xff), 8, false); // Get blue bits

		// Write file bits to least significant bits of each channel (last array position)
		for (int bit = 0; bit < bitsPerChannel && bitPos + 4 * (bit + 1) <= bits.length; bit++) {
			aBits[7 - bit] = bits[bitPos + (bit * 4)];
			rBits[7 - bit] = bits[bitPos + 1 + (bit * 4)];
			gBits[7 - bit] = bits[bitPos + 2 + (bit * 4)];
			bBits[7 - bit] = bits[bitPos + 3 + (bit * 4)];
		}

		// Return 32-bit int representing the color of the pixel, with some relevant bits stored in it
		return (nStag.bitArrayToInt(aBits, false) << 24) | (nStag.bitArrayToInt(rBits, false) << 16)
				| (nStag.bitArrayToInt(gBits, false)) << 8 | nStag.bitArrayToInt(bBits, false);
	}
}