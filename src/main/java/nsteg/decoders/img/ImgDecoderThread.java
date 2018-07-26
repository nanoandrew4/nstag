package nsteg.decoders.img;

import nsteg.encoders.img.ImgEncoder;
import nsteg.encoders.img.ImgEndState;
import nsteg.nsteg_utils.BitByteConv;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

public class ImgDecoderThread extends Thread {
	// Each thread has its own instance, so it can load values and return them to ImgEncoder, so it can continue submitting jobs
	private ImgEndState imgEndState = new ImgEndState();

	private BufferedImage img;
	private boolean running = true; // False will cause the thread to exit
	private boolean active = false; // Whether the thread is currently encoding information or is waiting for a job

	private int width, height;

	private byte[] fileBytes;
	private static int bitsPerChannel, numOfChannels;
	private int sx, sy, currByte, endByte;

	// Bits read from pixels are loaded to the buffer, for temporary storage, until the requested amount of them have been read
	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	/**
	 * Number of bits to be read from the image before re-assembling them into bytes and writing them to the file byte
	 * array. This is used to minimize the work done by the thread, at the expense of using more memory.
	 */
	private static final int BLOCK_SIZE = 1024;

	ImgDecoderThread(@NotNull BufferedImage img, int numOfChannels) {
		this.setDaemon(true);

		this.img = img;
		ImgDecoderThread.numOfChannels = numOfChannels;

		width = img.getWidth();
		height = img.getHeight();
	}

	static void setBitsPerChannel(int bitsPerChannel) {
		ImgDecoderThread.bitsPerChannel = bitsPerChannel;
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

	/**
	 * Submits a job for this thread to carry out. Initial encoding positions are given, and a ImgEndState instance is
	 * returned containing where the encoding process this thread will carry out will end, so that ImgEncoder can
	 * submit more jobs without having to wait for this one to finish.
	 *
	 * @param fileBytes Array to which to write the bytes extracted from the image. Array must be the size of the
	 *                  original file, since each thread will write to the correct place.
	 * @param sx        Starting 'x' coordinate in the image
	 * @param sy        Starting 'y' coordinate in the image
	 * @return ImgEndState instance containing ending positions of the starting values passed, so that jobs can quickly
	 * be submitted to other threads, without having to wait for this one to finish
	 */
	ImgEndState submitJob(@NotNull byte[] fileBytes, ArrayDeque<Byte> buffer, int sx, int sy, int bytesToRead, int byteStartPos) {
		if (active) {
			System.err.println("Thread was busy while attempting to submit job!");
			return null;
		}

		this.fileBytes = fileBytes;
		this.sx = sx;
		this.sy = sy;
		this.currByte = byteStartPos;
		this.endByte = currByte + bytesToRead;

		// Add leftover bits from previous encoding and then clear the original buffer
		this.buffer.addAll(buffer);
		buffer.clear();

		int bitsPerPixel = numOfChannels * bitsPerChannel;
		int bitsToRead = bytesToRead * Byte.SIZE - this.buffer.size();
		imgEndState.endX = (sx + (bitsToRead / bitsPerPixel)) % width;
		imgEndState.endY = sy + (sx + (bitsToRead / bitsPerPixel)) / width;
		imgEndState.endLSB = bitsToRead % bitsPerPixel;

		active = true;
		return imgEndState;
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
							fileBytes[currByte++] = (byte) BitByteConv.bitArrayToInt(ImgDecoder.loadFromBuffer(buffer, Byte.SIZE), true);
					}
					sx = 0;
				}

				buffer.clear();
				active = false;
			} else
				ImgEncoder.sleep(1);
		}
	}
}
