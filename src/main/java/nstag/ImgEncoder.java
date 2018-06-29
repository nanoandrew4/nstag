package nstag;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * This class will encode data to the specified image, using the specified number of least significant bit(s) in each
 * pixel to do so. It writes all data sequentially, so it should be decoded in that same order it was encoded, later on.
 * <p>
 * A nibble is required to store the number of LSBs in each channel that will be used for encoding, and subsequently,
 * decoding. In the event the image being worked on has three channels (RGB), the first two pixels are reserved for
 * storing this nibble, which means two channels in the second pixel go unused. So after writing the number of LSBs per
 * channel, values are reset to start encoding at the third pixel, as if no data had been written yet.
 */
public class ImgEncoder {

	private BufferedImage img; // Image to read (A)RGB data from and to write (A)ARGB modified data to
	private int x = 0, y = 0; // Pixel coords
	private int width, height; // Dims of img

	private int bitsPerChannel = 1, numOfChannels;
	private int nextChanToWrite; // Next channel, at current LSB position in current pixel, to be written to
	private int currLSB; // Current least significant bit position, for all channels, at the current pixel

	// Aids in buffering right amount of data, in order to fill each pixel properly
	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	ImgEncoder(BufferedImage origImg, int bitsPerChannel) {
		img = origImg;
		numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;
		width = img.getWidth();
		height = img.getHeight();

		// Encode desired LSBs per channel using only LSB of first (or first and second, depending on number of channels) pixel(s)
		encodeBits(nStag.intToBitArray(bitsPerChannel, 4, false));

		// Use two pixels for bitsPerChannel encoding, so restart encoding at 3rd pixel, if image only has three channels
		if (numOfChannels == 3) {
			x = 2;
			currLSB = nextChanToWrite = 0;
		}

		this.bitsPerChannel = bitsPerChannel;
	}

	/**
	 * Returns the image this Encoder works on. This should be called once all the data has been encoded, and the
	 * image containing the encoded data is ready to be written to the disk.
	 *
	 * @return Image containing whatever data has been encoded before this method was called
	 */
	public BufferedImage getImg() {
		return img;
	}

	/**
	 * Encodes an array of bits into the image. This is done by writing the data bits to the least significant bit(s)
	 * of the (A)RGB channels of each pixel of the image, sequentially. The number of LSBs used in each channel is
	 * specified when instantiating the object this method is called from.
	 *
	 * @param bitsToEncode Array of type byte, containing only bits, that are to be encoded in the image
	 */
	public void encodeBits(byte[] bitsToEncode) {
		int pos = 0;
		for (; y < height; y++) {
			for (; x < width; ) {
				if (pos >= bitsToEncode.length && buffer.isEmpty())
					return;

				while (buffer.size() < numOfChannels * bitsPerChannel && pos < bitsToEncode.length)
					buffer.add(bitsToEncode[pos++]);
				img.setRGB(x, y, insertDataToPixel(img.getRGB(x, y)));

				if ((currLSB %= bitsPerChannel) == 0)
					x++;
			}
			x = 0;
		}
	}

	/**
	 * Encodes an array of bytes into the image, by breaking each byte down into an array of 8 bits, and calling
	 * encodeBits(byte[] bitsToEncode). See that method for more information.
	 *
	 * @param bytesToEncode Array of bytes to be encoded in the image
	 */
	public void encodeBytes(byte[] bytesToEncode) {
		for (byte b : bytesToEncode)
			encodeBits(nStag.intToBitArray(b, 8, true));
	}

	/**
	 * Writes bits from the buffer to the various LSBs in the various channels the current pixel being worked on
	 * has. Because RGB images have 3 channels, encoding data usually means that you will finish encoding before
	 * exhausting all the LSBs in all the channels of the last necessary pixel.
	 *
	 * If one byte (8 bits) were encoded in an RGB image, we would need 3 pixels to hold the 8 bits (assuming we
	 * used one LSB), but would have one bit left, in which no data would be written, which would be a waste, aside
	 * from complicating the decoding process. Therefore, this method remembers where it left off, and will continue
	 * encoding at the next free LSB; in the case of the previous example, it will start writing whatever data is to
	 * be encoded next at the third channel of the third pixel, meaning that all the data is encoded sequentially,
	 * with no breaks. So efficient :D
	 *
	 * @param orig Original 32-bit argb int representing the color of the pixel
	 * @return Modified 32-bit argb int representing the new color of the pixel
	 */
	private int insertDataToPixel(int orig) {
		byte[] aBits = nStag.intToBitArray(((orig >> 24) & 0xff), 8, false); // Get original alpha bits
		byte[] rBits = nStag.intToBitArray(((orig >> 16) & 0xff), 8, false); // Get original red bits
		byte[] gBits = nStag.intToBitArray(((orig >> 8) & 0xff), 8, false); // Get original green bits
		byte[] bBits = nStag.intToBitArray((orig & 0xff), 8, false); // Get original blue bits

		// Mod bit values, in order to encode bits from the buffer. Read method doc for more info
		for (; currLSB < bitsPerChannel && !buffer.isEmpty(); ) {
			if (nextChanToWrite == 0) {
				rBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 1;
			}

			if (!buffer.isEmpty() && nextChanToWrite == 1) {
				gBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 2;
			} else if (buffer.isEmpty())
				break;

			if (!buffer.isEmpty() && nextChanToWrite == 2) {
				bBits[7 - currLSB] = buffer.pop();
				// If image has alpha channel, continue, otherwise change LSB and restart
				nextChanToWrite = 3 % numOfChannels;
				if (numOfChannels == 3) // If no alpha channel, go on to next LSB, if possible
					currLSB++;
			} else if (buffer.isEmpty())
				break;

			if (nextChanToWrite == 3 && !buffer.isEmpty()) {
				aBits[7 - currLSB] = buffer.pop();
				nextChanToWrite = 0;
				currLSB++;
			}
		}

		// Return 32-bit int representing the color of the pixel, with the encoded bits from the buffer
		return (nStag.bitArrayToInt(aBits, false) << 24) | (nStag.bitArrayToInt(rBits, false) << 16)
				| (nStag.bitArrayToInt(gBits, false)) << 8 | nStag.bitArrayToInt(bBits, false);
	}
}