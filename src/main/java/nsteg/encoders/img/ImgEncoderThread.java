package nsteg.encoders.img;

import nsteg.nsteg_utils.BitByteConv;
import nsteg.threads.ImgThread;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;

/**
 * This class operates on an image to encode data passed on by ImgEncoder. It does so in a way that allows threaded
 * encoding, in order to use system resources fully. In essence, ImgEncoder submits a job, and this class tells it
 * where it will finish encoding the passed data, so that ImgEncoder can then assign more jobs to other instances of
 * this class.
 */
public class ImgEncoderThread extends ImgThread {
	private PxBitModder bitModder;

	private byte[] filesBytes;
	private int currFileArrByte, endByte;

	// Used to prevent race conditions between threads.
	private static int[][] threadPixPos = new int[Runtime.getRuntime().availableProcessors()][2];
	private int threadID;

	static {
		for (int j = 0; j < 2; j++)
			for (int i = 0; i < threadPixPos.length; i++)
				threadPixPos[i][j] = -1;
	}

	/**
	 * Initializes a thread, and assigns the image that will be worked on.
	 *
	 * @param img           BufferedImage object to encode the data to
	 * @param numOfChannels Number of channels in the BufferedImage
	 * @param threadID      ID for the thread safety mechanism, number 0-availableProcessors. Use iteration count as
	 *                      thread ID  when initializing the threads in another class
	 */
	ImgEncoderThread(@NotNull BufferedImage img, int numOfChannels, int threadID) {
		super(img, numOfChannels);
		this.threadID = threadID;
	}

	/**
	 * Prevents race condition between multiple threads when modifying a pixel. In essence, disallows multiple threads
	 * from modifying the same pixel at the same time, one must wait for the other to release the lock on that pixel.
	 *
	 * @param x X coordinate of the desired pixel in the BufferedImage
	 * @param y Y coordinate of the desired pixel in the BufferedImage
	 * @return True if the pixel is available, false if it is being used by another thread
	 */
	private synchronized boolean requestLock(int x, int y) {
		for (int[] i : threadPixPos)
			if (i[0] == x && i[1] == y)
				return false;
		threadPixPos[threadID][0] = x;
		threadPixPos[threadID][1] = y;
		return true;
	}

	/**
	 * Waits until the pixel is released by another thread, if another thread was using it.
	 *
	 * @param x X coordinate of the desired pixel in the BufferedImage
	 * @param y Y coordinate of the desired pixel in the BufferedImage
	 */
	private void waitForLock(int x, int y) {
		while (!requestLock(x, y))
			sleepMillis(1);
	}

	/**
	 * Releases the pixel being worked on by the calling thread, so that other threads may work on it if necessary.
	 */
	private synchronized void release() {
		threadPixPos[threadID][0] = -1;
		threadPixPos[threadID][1] = -1;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				// Create bit array out of the bytes that are to be encoded, for easy insertion
				byte[] currByteBits = new byte[(endByte - currFileArrByte) * Byte.SIZE];
				int bitPos = 0;
				for (; currFileArrByte < endByte; currFileArrByte++) {
					System.arraycopy(BitByteConv.intToBitArray(filesBytes[currFileArrByte], Byte.SIZE), 0,
									 currByteBits, bitPos, Byte.SIZE);
					bitPos += Byte.SIZE;
				}

				/*
				 * Determines if the pixel should be locked to prevent race conditions. Only necessary when working on
				 * the first and last pixel that will be modified by this thread.
				 */
				boolean needsLocking;
				int bitsPerPixel = numOfChannels * LSBsToUse;
				int y = sy, x = sx;

				bitModder.resetCurrBit();
				while (bitModder.getCurrBit() < currByteBits.length) {
					needsLocking = (bitModder.getCurrBit() < bitsPerPixel ||
									bitModder.getCurrBit() >= currByteBits.length - bitsPerPixel);
					if (needsLocking)
						waitForLock(x, y);
					img.setRGB(x, y, bitModder.insertDataToPixel(img.getRGB(x, y), currByteBits)); // Fills pixel
					if (needsLocking)
						release();

					bitModder.setCurrLSB(0);
					if (++x == width) {
						x = 0;
						y++;
					}
				}

				active = false;
			} else
				sleepMillis(5);
		}
	}

	/**
	 * Submits a job for this thread to carry out. Initial encoding positions are given, and a imgEndState instance is
	 * returned containing where the encoding process this thread will carry out will end, so that ImgEncoder can
	 * submit more jobs to other threads without having to wait for this one to finish.
	 *
	 * @param filesBytes      Byte array that was passed through the encodeBytes() method. Each thread will write
	 *                        the bytes that correspond to it
	 * @param sByte           Byte position to start fetching data for encoding in the filesBytes array
	 * @param bytesToWrite    Number of bytes this thread should encode into the image
	 * @param sx              Starting 'x' coordinate in the image
	 * @param sy              Starting 'y' coordinate in the image
	 * @param sLSB            Starting least significant bit position
	 * @param nextChanToWrite First channel to write to
	 * @return ImgEndState instance containing ending positions of the starting values passed, so that jobs can quickly
	 * be submitted to other threads, without having to wait for this one to finish
	 */
	ImgEndState submitJob(@NotNull byte[] filesBytes, int sByte, int bytesToWrite, int sx, int sy, int sLSB,
						  int nextChanToWrite) {
		if (active) {
			System.err.println("Thread was busy while attempting to submit job!");
			return null;
		}

		this.filesBytes = filesBytes;
		this.sx = sx;
		this.sy = sy;
		this.currFileArrByte = sByte;
		this.endByte = sByte + bytesToWrite;
		bitModder = new PxBitModder(numOfChannels, LSBsToUse, sLSB, nextChanToWrite);

		active = true;

		/*
		 * The following calculations to determine end positions may seem a bit obscure, but can be quickly worked out
		 * on paper. The XY end coordinates of the last pixel written to can be simply calculated by looking at how
		 * many bits each pixel can take, and calculating the offset based on the number of currByteBits to write.
		 * The X coord requires an extra step, in case that the LSB wraps around, an extra pixel is required, which
		 * is what the second statement dealing with endX does.
		 *
		 * The end channel is also fairly straightforward, since the channel simply loops around, which a modulus
		 * operation lets us know where the constant looping through the possible channels will end.
		 *
		 * The end least significant bit is a bit more obscure, but it is basically calculated by determining the
		 * number of times that the available channels will be cycled through in order to encode the data, then adding
		 * that to the initial starting LSB, and using the modulo operator to get the final LSB.
		 */
		int bitsToWrite = bytesToWrite * Byte.SIZE;
		int bitsPerPixel = LSBsToUse * numOfChannels;
		endState.endX = (sx + (bitsToWrite / bitsPerPixel)) % width;
		endState.endX += (sLSB + ((nextChanToWrite + (bitsToWrite % bitsPerPixel)) / numOfChannels)) / LSBsToUse;
		endState.endY = sy + ((sx + (bitsToWrite / bitsPerPixel)) / width);
		endState.endChan = (nextChanToWrite + bitsToWrite) % numOfChannels;
		endState.endLSB = (sLSB + ((nextChanToWrite + (bitsToWrite % bitsPerPixel)) / numOfChannels)) % LSBsToUse;

		return endState;
	}
}