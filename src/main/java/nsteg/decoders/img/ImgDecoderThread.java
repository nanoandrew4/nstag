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
	 * @param fileBytes
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
		this.buffer.addAll(buffer);
		buffer.clear();
		this.currByte = byteStartPos;
		this.endByte = currByte + bytesToRead;

		int bitsPerPixel = numOfChannels * bitsPerChannel;
		int bitsToRead = bytesToRead * Byte.SIZE;
		imgEndState.endX = (sx + (bitsToRead / bitsPerPixel)) % width;
		imgEndState.endY = sy + (sx + (bitsToRead / bitsPerPixel)) / width;
		imgEndState.endLSB = (this.buffer.size() + bitsToRead) % bitsPerPixel;

//		System.out.println(currByte + " eLSB: " + imgEndState.endLSB);
		System.out.println("Init buff size: " + this.buffer.size());

		active = true;
		return imgEndState;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				for (int y = sy; y < height && currByte < endByte; y++) {
					for (int x = sx; x < width && currByte < endByte; ) {
						while (buffer.size() < Byte.SIZE && x < width && currByte < endByte)
							extractDataFromPixel(img.getRGB(x++, y));

						while (buffer.size() >= Byte.SIZE && currByte < endByte)
							fileBytes[currByte++] = (byte) BitByteConv.bitArrayToInt(loadFromBuffer(Byte.SIZE), true);
					}
					sx = 0;
				}
//				System.out.println(currByte + " Leftover: " + buffer.size());

				buffer.clear();
				active = false;
			} else
				ImgEncoder.sleep(1);
		}
	}

	/**
	 * Loads a specified number of bits from the buffer into an array.
	 *
	 * @param bitsToRead Number of bits to load from the buffer to the array
	 * @return Array of bits of specified dimensions
	 */
	private byte[] loadFromBuffer(int bitsToRead) {
		byte[] bits = new byte[bitsToRead];
		for (int i = 0; i < bitsToRead; i++)
			bits[i] = buffer.pop();

		return bits;
	}

	/**
	 * Retrieves bits from the file that was encoded in the image by reading and storing the least significant bit(s)
	 * of each channel from the pixel (A)RGB value passed. The LSBs read are stored in the buffer, which is handled by
	 * the calling method (readBits(int bitsToRead)).
	 *
	 * @param orig 32-bit argb int representing the colors the values of the 4 color channels
	 */
	private void extractDataFromPixel(int orig) {
		byte[] aBits = BitByteConv.intToBitArray((orig >> 24) & 0xff, Byte.SIZE); // Get alpha channel value
		byte[] rBits = BitByteConv.intToBitArray((orig >> 16) & 0xff, Byte.SIZE); // Get red channel value
		byte[] gBits = BitByteConv.intToBitArray((orig >> 8) & 0xff, Byte.SIZE); // Get green channel value
		byte[] bBits = BitByteConv.intToBitArray(orig & 0xff, Byte.SIZE); // Get blue channel value

		// Write least significant bit(s) from the color channels to the queue of bits to recover the encoded file
		for (int lsb = 0; lsb < bitsPerChannel; lsb++) {
			buffer.add(rBits[rBits.length - 1 - lsb]);
			buffer.add(gBits[gBits.length - 1 - lsb]);
			buffer.add(bBits[bBits.length - 1 - lsb]);
			if (numOfChannels == 4)
				buffer.add(aBits[aBits.length - 1 - lsb]);
		}
	}
}
