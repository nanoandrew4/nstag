package nsteg.img.encoder;

import nsteg.nsteg_utils.BitByteConv;

import java.awt.image.BufferedImage;

public class EncoderThread extends Thread {
	private EndState endState = new EndState();

	private BufferedImage img;
	private boolean running = true;
	private boolean active = false;

	private int width, height;

	private byte[] bitsToWrite;
	private static int bitsPerChannel, numOfChannels;
	private int sx, sy, currLSB, nextChanToWrite, currBit;

	EncoderThread(BufferedImage img, int numOfChannels) {
		this.setDaemon(true);

		this.img = img;
		EncoderThread.numOfChannels = numOfChannels;

		width = img.getWidth();
		height = img.getHeight();
	}

	public static void setBitsPerChannel(int bitsPerChannel) {
		EncoderThread.bitsPerChannel = bitsPerChannel;
	}

	void stopRunning() {
		running = false;
	}

	boolean isActive() {
		return active;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				for (int y = sy; y < height && currBit < bitsToWrite.length; y++) {
					for (int x = sx; x < width && currBit < bitsToWrite.length; ) {
						img.setRGB(x, y, insertDataToPixel(img.getRGB(x, y)));

						if ((currLSB %= bitsPerChannel) == 0 && nextChanToWrite == 0)
							x++;
					}
					sx = 0;
				}
				active = false;
			} else
				ImgEncoder.sleep(1);
		}
	}

	EndState submitJob(byte[] bitsToWrite, int sx, int sy, int currLSB, int nextChanToWrite, boolean padded) {
		if (active)
			return null;

		this.bitsToWrite = bitsToWrite;
		this.sx = sx;
		this.sy = sy;
		this.currLSB = currLSB;
		this.nextChanToWrite = nextChanToWrite;
		currBit = 0;

		active = true;

		endState.endX = (sx + (bitsToWrite.length / (bitsPerChannel * numOfChannels))) % width + (padded ? 1 : 0); // Will always pad to finish the pixel
		endState.endY = sy + ((sx + (bitsToWrite.length / (bitsPerChannel * numOfChannels))) / width);
		endState.endNextChanToWrite = (nextChanToWrite + bitsToWrite.length % numOfChannels) % numOfChannels;
		endState.endLSB = (currLSB + ((nextChanToWrite + (bitsToWrite.length % (bitsPerChannel * numOfChannels)))) / numOfChannels) % bitsPerChannel;

		return endState;
	}

	/**
	 * Writes bits from the buffer to the various LSBs in the various channels the current pixel being worked on
	 * has. Because RGB images have 3 channels, encoding data usually means that you will finish encoding before
	 * exhausting all the LSBs in all the channels of the last necessary pixel.
	 * <p><br>
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
		byte[] aBits = null;
		if (numOfChannels == 4)
			aBits = BitByteConv.intToBitArray(((orig >> 24) & 0xff), Byte.SIZE, false); // Get original alpha bits
		byte[] rBits = BitByteConv.intToBitArray(((orig >> 16) & 0xff), Byte.SIZE, false); // Get original red bits
		byte[] gBits = BitByteConv.intToBitArray(((orig >> 8) & 0xff), Byte.SIZE, false); // Get original green bits
		byte[] bBits = BitByteConv.intToBitArray(orig & 0xff, Byte.SIZE, false); // Get original blue bits

		// Mod bit values, in order to encode bits from the buffer. Read method doc for more info
		for (; currLSB < bitsPerChannel && currBit < bitsToWrite.length; ) {
			if (nextChanToWrite == 0) {
				rBits[rBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				nextChanToWrite = 1;
			}

			if (currBit < bitsToWrite.length && nextChanToWrite == 1) {
				gBits[gBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				nextChanToWrite = 2;
			} else if (currBit >= bitsToWrite.length)
				break;

			if (currBit < bitsToWrite.length && nextChanToWrite == 2) {
				bBits[bBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				// If image has alpha channel, continue to it, otherwise change LSB and restart
				nextChanToWrite = 3 % numOfChannels;
				if (numOfChannels == 3) // If no alpha channel, go on to next LSB, if possible
					currLSB++;
			} else if (currBit >= bitsToWrite.length)
				break;

			if (nextChanToWrite == 3 && currBit < bitsToWrite.length) {
				aBits[aBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				nextChanToWrite = 0;
				currLSB++;
			}
		}

		// Return 32-bit int representing the color of the pixel, with the encoded bits from the buffer

		if (numOfChannels == 3)
			return (BitByteConv.bitArrayToInt(rBits, false) << 16) |
					(BitByteConv.bitArrayToInt(gBits, false) << 8) | BitByteConv.bitArrayToInt(bBits, false);
		else
			return (BitByteConv.bitArrayToInt(aBits, false) << 24) | (BitByteConv.bitArrayToInt(rBits, false) << 16)
					| (BitByteConv.bitArrayToInt(gBits, false) << 8) | BitByteConv.bitArrayToInt(bBits, false);
	}
}