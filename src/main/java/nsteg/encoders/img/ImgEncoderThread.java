package nsteg.encoders.img;

import nsteg.nsteg_utils.BitByteConv;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;

/**
 * This class operates on an image to encode data passed on by ImgEncoder. It does so in a way that allows threaded
 * encoding, in order to use system resources fully. In essence, ImgEncoder submits a job, and this class tells it
 * where it will finish encoding the passed data, so that ImgEncoder can then assign more jobs to other instances of
 * this class.
 */
public class ImgEncoderThread extends Thread {
	// Each thread has its own instance, so it can load values and return them to ImgEncoder, so it can continue submitting jobs
	private ImgEndState imgEndState = new ImgEndState();

	private BufferedImage img;
	private boolean running = true; // False will cause the thread to exit
	private boolean active = false; // Whether the thread is currently encoding information or is waiting for a job

	private int width, height;

	private byte[] bitsToWrite;
	private static int bitsPerChannel, numOfChannels;
	private int sx, sy, currLSB, nextChanToWrite, currBit;

	ImgEncoderThread(@NotNull BufferedImage img, int numOfChannels) {
		this.setDaemon(true);

		this.img = img;
		ImgEncoderThread.numOfChannels = numOfChannels;

		width = img.getWidth();
		height = img.getHeight();
	}

	static void setBitsPerChannel(int bitsPerChannel) {
		ImgEncoderThread.bitsPerChannel = bitsPerChannel;
	}

	/**
	 * Ends loop in the run() method, effectively terminating the thread.
	 */
	void stopRunning() {
		running = false;
	}

	/**
	 * Returns whether this thread is busy encoding bits to the image.
	 *
	 * @return True if it is encoding data, false if it is available to encode data
	 */
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

						// If all data has been read from the pixel, move to next one
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

	/**
	 * Submits a job for this thread to carry out. Initial encoding positions are given, and a ImgEndState instance is
	 * returned containing where the encoding process this thread will carry out will end, so that ImgEncoder can
	 * submit more jobs without having to wait for this one to finish.
	 *
	 * @param bitsToWrite     Array of bits to write to encode
	 * @param sx              Starting 'x' coordinate in the image
	 * @param sy              Starting 'y' coordinate in the image
	 * @param sLSB            Starting least significant bit position
	 * @param nextChanToWrite First channel to write to
	 * @return ImgEndState instance containing ending positions of the starting values passed, so that jobs can quickly
	 * be submitted to other threads, without having to wait for this one to finish
	 */
	ImgEndState submitJob(@NotNull byte[] bitsToWrite, int sx, int sy, int sLSB, int nextChanToWrite) {
		if (active) {
			System.err.println("Thread was busy while attempting to submit job!");
			return null;
		}

		this.bitsToWrite = bitsToWrite;
		this.sx = sx;
		this.sy = sy;
		this.currLSB = sLSB;
		this.nextChanToWrite = nextChanToWrite;
		currBit = 0; // Start reading bitsToWrite from start

		active = true;

		/*
		 * The following calculations to determine end positions may seem a bit obscure, but can be quickly worked out
		 * on paper. The XY end coordinates of the last pixel written to can be simply calculated by looking at how
		 * many bits each pixel can take, and calculating the offset based on the number of bits to write. The X coord
		 * requires an extra step, in case that the LSB wraps around, an extra pixel is required, which is what the
		 * second statement dealing with endX does.
		 *
		 * The end channel is also fairly straightforward, since the channel simply loops around, which a modulus
		 * operation lets us know where the constant looping through the possible channels will end.
		 *
		 * The end least significant bit is a bit more obscure, but it is basically calculated by determining the
		 * number of times that the available channels will be cycled through in order to encode the data, then adding
		 * that to the initial starting LSB, and using the modulo operator to get the final LSB.
		 */
		imgEndState.endX = (sx + (bitsToWrite.length / (bitsPerChannel * numOfChannels))) % width;
		imgEndState.endX += (sLSB + ((nextChanToWrite + (bitsToWrite.length % (bitsPerChannel * numOfChannels))) / numOfChannels)) / bitsPerChannel;
		imgEndState.endY = sy + ((sx + (bitsToWrite.length / (bitsPerChannel * numOfChannels))) / width);
		imgEndState.endNextChanToWrite = (nextChanToWrite + bitsToWrite.length) % numOfChannels;
		imgEndState.endLSB = (sLSB + ((nextChanToWrite + (bitsToWrite.length % (bitsPerChannel * numOfChannels))) / numOfChannels)) % bitsPerChannel;

		return imgEndState;
	}

	/**
	 * Writes bits to the various LSBs in the various channels the current pixel being worked on has. Because RGB images
	 * have 3 channels, encoding data usually means that you will finish encoding before exhausting all the LSBs in all
	 * the channels of the last necessary pixel.
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
			aBits = BitByteConv.intToBitArray(((orig >> 24) & 0xff), Byte.SIZE); // Get original alpha bits
		byte[] rBits = BitByteConv.intToBitArray(((orig >> 16) & 0xff), Byte.SIZE); // Get original red bits
		byte[] gBits = BitByteConv.intToBitArray(((orig >> 8) & 0xff), Byte.SIZE); // Get original green bits
		byte[] bBits = BitByteConv.intToBitArray(orig & 0xff, Byte.SIZE); // Get original blue bits

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