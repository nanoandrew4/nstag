package nstag;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

public class ImgEncoder {
	private BufferedImage img;
	private int x = 0, y = 0;
	private int width, height;

	private int bitsPerChannel = 1, nextChanToWrite = 0, numOfChannels;
	private int currLSB = 0;
	private boolean hasAlpha;

	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	// Seq writes
	ImgEncoder(BufferedImage origImg, int bitsPerChannel) {
		img = origImg;
		hasAlpha = img.getColorModel().hasAlpha();
		numOfChannels = hasAlpha ? 4 : 3;
		width = img.getWidth();
		height = img.getHeight();

		encodeBits(nStag.intToBitArray(bitsPerChannel, 4, false));

		this.bitsPerChannel = bitsPerChannel;
	}

	public BufferedImage getImg() {
		return img;
	}

	public void encodeBits(byte[] bitsToEncode) {
		int pos = 0;
		for (; y < height; y++) {
			for (; x < width; x++) {
				if (pos >= bitsToEncode.length && buffer.isEmpty())
					return;

				while (buffer.size() < numOfChannels * bitsPerChannel && pos < bitsToEncode.length)
					buffer.add(bitsToEncode[pos++]);

				img.setRGB(x, y, insertDataToPixel(img.getRGB(x, y)));
			}
			x = 0;
		}
	}

	public void encodeBytes(byte[] bytesToEncode, int startNibble) {
		byte[] byteBits = new byte[bitsPerChannel * numOfChannels];
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
				img.setRGB(x, y, insertDataToPixel(img.getRGB(x, y)));
			}
			x = 0;
		}
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
		for (; currLSB < bitsPerChannel && !buffer.isEmpty(); currLSB++) {
			rBits[7 - currLSB] = buffer.pop();

			if (!buffer.isEmpty()) {
				gBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 2;
			} else if (buffer.isEmpty())
				break;
			
			if (!buffer.isEmpty() && nextChanToWrite == 2) {
				bBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 3 % numOfChannels;
			}
			else if (buffer.isEmpty())
				break;

			if (nextChanToWrite == 3 && !buffer.isEmpty()) {
				aBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 0;
			}
		}

		currLSB %= bitsPerChannel;

		// Return 32-bit int representing the color of the pixel, with some relevant bits stored in it
		return (nStag.bitArrayToInt(aBits, false) << 24) | (nStag.bitArrayToInt(rBits, false) << 16)
				| (nStag.bitArrayToInt(gBits, false)) << 8 | nStag.bitArrayToInt(bBits, false);
	}
}