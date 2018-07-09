package nsteg.img.encoder;

import nsteg.nsteg_utils.BitByteConv;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * This class will encode data to the specified image, using the specified number of least significant bit(s) in each
 * pixel to do so. It writes all data sequentially, so it should be decoded in that same order it was encoded, later on.
 * <p><br>
 * A nibble is required to store the number of LSBs in each channel that will be used for encoding, and subsequently,
 * decoding. In the event the image being worked on has three channels (RGB), the first two pixels are reserved for
 * storing this nibble, which means two channels in the second pixel go unused. So after writing the number of LSBs per
 * channel, values are reset to start encoding at the third pixel, as if no data had been written yet. If the image has
 * four channels (ARGB), only the first pixel is used for encoding, and data encoding starts at the second pixel, which
 * means no space is wasted.
 */
public class ImgEncoder {
	private BufferedImage img; // Image to read (A)RGB data from and to write (A)RGB modified data to
	private int x = 0, y = 0; // Pixel coords

	private int bitsPerChannel = 1, numOfChannels;
	private int nextChanToWrite; // Next channel, at current LSB position in current pixel, to be written to
	private int currLSB; // Current least significant bit position, for all channels, at the current pixel

	private final int THREE_CHAN_BPC_LCM = 645120;
	private final int FOUR_CHAN_BPC_LCM = 860160;

	// Aids in buffering right amount of data, in order to fill each pixel properly
	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	private EncoderThread[] encThreads = new EncoderThread[Runtime.getRuntime().availableProcessors() - 1];

	/**
	 * Initializes an ImgEncoder instance with the given image, determines the number of channels in the image,
	 * and encodes the number of LSBs that will be used in each channel into the image.
	 *
	 * @param origImg        Image to encode data into
	 * @param bitsPerChannel Number of least significant bits to use in each channel
	 */
	public ImgEncoder(BufferedImage origImg, int bitsPerChannel) {
		img = origImg;
		numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;

		for (int i = 0; i < encThreads.length; i++) {
			encThreads[i] = new EncoderThread(img, numOfChannels);
			encThreads[i].start();
		}

		// Encode desired LSBs per channel using only LSB of first (or first and second, depending on number of channels) pixel(s)
		EncoderThread.setBitsPerChannel(1);
		encodeBits(BitByteConv.intToBitArray(bitsPerChannel, 4, false), false);

		while (encThreads[0].isActive())
			sleep(2);

		// Use two pixels for bitsPerChannel encoding, so restart encoding at 3rd pixel, if image only has three channels
		if (numOfChannels == 3) {
			x = 2;
			currLSB = nextChanToWrite = 0;
		}

		EncoderThread.setBitsPerChannel(bitsPerChannel);
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

	static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

	private void encodeBits(byte[] bitsToEncode, boolean padding) {
		for (int i = 0; i < encThreads.length; i %= encThreads.length) {
			if (!encThreads[i].isActive()) {
				EndState endState = encThreads[i].submitJob(bitsToEncode, x, y, currLSB, nextChanToWrite, padding);
				x = endState.endX;
				y = endState.endY;
				currLSB = endState.endLSB;
				nextChanToWrite = endState.endNextChanToWrite;
				break;
			} else
				sleep(1);
			i++;
		}
	}

	/**
	 * Encodes an array of bits into the image. This is done by writing the data bits to the least significant bit(s)
	 * of the (A)RGB channels of each pixel of the image, sequentially. The number of LSBs used in each channel is
	 * specified when instantiating the object this method is called from.
	 *
	 * @param bitsToEncode Array containing only bits, that are to be encoded in the image
	 */

	public void encodeBits(byte[] bitsToEncode) {
		for (int i = 0; i < encThreads.length; i %= encThreads.length) {
			if (!encThreads[i].isActive()) {
				EndState endState = encThreads[i].submitJob(bitsToEncode, x, y, currLSB, nextChanToWrite, false);
				x = endState.endX;
				y = endState.endY;
				currLSB = endState.endLSB;
				nextChanToWrite = endState.endNextChanToWrite;
				break;
			} else
				sleep(1);
			i++;
		}
	}

	/**
	 * Encodes an array of bytes into the image, by breaking each byte down into an array of 8 bits, and calling
	 * encodeBits(byte[] bitsToEncode). See that method for more information.
	 *
	 * @param bytesToEncode Array of bytes to be encoded in the image
	 */
	public void encodeBytes(byte[] bytesToEncode) {
		int currByte = 0;
		int chunkByteSize = (numOfChannels == 3 ? THREE_CHAN_BPC_LCM : FOUR_CHAN_BPC_LCM) / Byte.SIZE;
		while (currByte < bytesToEncode.length) {
			int currBitPos = 0;

			int numOfBitsToEncode;
			if (currByte + chunkByteSize < bytesToEncode.length)
				numOfBitsToEncode = chunkByteSize * Byte.SIZE + (currLSB > 0 || nextChanToWrite > 0 ? (bitsPerChannel - currLSB) * numOfChannels - nextChanToWrite : 0);
			else
				numOfBitsToEncode = (bytesToEncode.length - currByte) * Byte.SIZE + buffer.size();

			byte[] bitsToEncode = new byte[numOfBitsToEncode];

			while (!buffer.isEmpty())
				bitsToEncode[currBitPos++] = buffer.pop();

			for (; currByte < bytesToEncode.length && currBitPos < bitsToEncode.length; currByte++) {
				byte[] bits = BitByteConv.intToBitArray(bytesToEncode[currByte], Byte.SIZE, true);
				if (currBitPos + Byte.SIZE <= bitsToEncode.length)
					System.arraycopy(bits, 0, bitsToEncode, currBitPos, Byte.SIZE);
				else {
					System.arraycopy(bits, 0, bitsToEncode, currBitPos, bitsToEncode.length - currBitPos);
					for (int i = bitsToEncode.length - currBitPos; i < Byte.SIZE; i++)
						buffer.add(bits[i]);
				}

				currBitPos += Byte.SIZE;
			}

			boolean padding = currLSB > 0 || nextChanToWrite > 0;

			encodeBits(bitsToEncode, padding);
		}
	}

	public void stopThreads() {
		for (int i = 0; i < encThreads.length; ) {
			if (!encThreads[i].isActive()) {
				encThreads[i].stopRunning();
				i++;
			} else
				sleep(1);
		}
	}
}