package nsteg.decoders.img;

import nsteg.encoders.img.ImgEndState;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.threads.ImgThread;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

public class ImgDecoderThread extends ImgThread {
	private byte[] byteArr;

	// Bits read from pixels are loaded to the buffer, for temporary storage, until the requested amount of them have been read
	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	/**
	 * Number of bits to be read from the image before re-assembling them into bytes and writing them to the file byte
	 * array. This is used to minimize the work done by the thread, at the expense of using more memory.
	 */
	private static final int BLOCK_SIZE = 1024;

	/**
	 * Initialize the thread with the BufferedImage that will be operated on, and the number of channels that the image
	 * has.
	 *
	 * @param img           BufferedImage to operate on
	 * @param numOfChannels Number of channels in the BufferedImage
	 */
	ImgDecoderThread(@NotNull BufferedImage img, int numOfChannels) {
		super(img, numOfChannels);
	}

	/**
	 * Submits a job for this thread to carry out. Initial encoding positions are given, and a ImgEndState instance is
	 * returned containing where the encoding process this thread will carry out will end, so that ImgEncoder can
	 * submit more jobs without having to wait for this one to finish.
	 *
	 * @param byteArr Array to which to write the bytes extracted from the image. Array must be the size of the
	 *                original file, since each thread will write to the correct place.
	 * @param sx      Starting 'x' coordinate in the image
	 * @param sy      Starting 'y' coordinate in the image
	 * @return ImgEndState instance containing ending positions of the starting values passed, so that jobs can quickly
	 * be submitted to other threads, without having to wait for this one to finish
	 */
	ImgEndState submitJob(@NotNull byte[] byteArr, @NotNull ArrayDeque<Byte> buffer, int sx, int sy, int bytesToRead, int byteStartPos) {
		if (active) {
			System.err.println("Thread was busy while attempting to submit job!");
			return null;
		}

		this.byteArr = byteArr;
		this.sx = sx;
		this.sy = sy;
		this.currByte = byteStartPos;
		this.endByte = currByte + bytesToRead;

		// Add leftover bits from previous decoding and then clear the original buffer
		this.buffer.addAll(buffer);
		buffer.clear();

		active = true;

		int bitsPerPixel = numOfChannels * LSBsToUse;
		int bitsToRead = bytesToRead * Byte.SIZE - this.buffer.size();
		// Determine where the decoding process will end, so that other threads can be started where this one leaves off
		endState.endX = (sx + (bitsToRead / bitsPerPixel)) % width;
		endState.endY = sy + (sx + (bitsToRead / bitsPerPixel)) / width;
		endState.endLSB = bitsToRead % bitsPerPixel;

		return endState;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				for (int y = sy; y < height && currByte < endByte; y++) {
					for (int x = sx; x < width && currByte < endByte; ) {
						// Read bits from the image
						while (buffer.size() < BLOCK_SIZE && x < width)
							ImgDecoder.extractDataFromPixel(buffer, img.getRGB(x++, y));

						// Assemble read bits into bytes and write them to the file byte array
						while (buffer.size() >= Byte.SIZE && currByte < endByte)
							byteArr[currByte++] = (byte) BitByteConv.bitArrayToInt(ImgDecoder.loadFromBuffer(buffer, Byte.SIZE), true);
					}
					sx = 0;
				}

				// Clear buffer in case there are leftovers and this thread is reused
				buffer.clear();
				active = false;
			} else
				sleepMillis(10);
		}
	}
}
