package nsteg.encoders.img;

import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Crypto;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;

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
public class ImgEncoder extends Encoder {
	private ImgEncoderThread[] encThreads = new ImgEncoderThread[Runtime.getRuntime().availableProcessors()];

	private BufferedImage img; // Image to read (A)RGB data from and to write (A)RGB modified data to
	private int x = 0, y = 0; // Current pixel coords

	private int nextChanToWrite; // Next channel, at current LSB position in current pixel, to be written to
	private int currLSB; // Current least significant bit position, for all channels, at the current pixel

	private int width, LSBsToUse = 1, currBit, numOfChannels;

	/**
	 * Initializes an ImgEncoder instance with the given image, determines the number of channels in the image,
	 * and encodes the number of LSBs that will be used in each channel into the image.
	 *
	 * @param origImg   Image to encode data into
	 * @param LSBsToUse Number of least significant bits to use in each channel
	 */
	public ImgEncoder(@NotNull BufferedImage origImg, int LSBsToUse) {
		img = origImg;
		numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;

		width = img.getWidth();

		initThreads(numOfChannels, LSBsToUse);
		this.LSBsToUse = LSBsToUse;

		// Use two pixels for LSBsToUse encoding, so restart encoding at 3rd pixel, if image only has three channels
		if (numOfChannels == 3) {
			x = 2;
			currLSB = nextChanToWrite = 0;
		}
	}

	// See abstract method declaration
	public boolean doesFileFit(int fileSizeInBits, int numOfFiles, int LSBsToUse, boolean encrypted) {
		long requiredBits = LSB_BITS_COUNT + (2 * SIZE_BITS_COUNT) + fileSizeInBits + SIZE_BITS_COUNT +
							(SIZE_BITS_COUNT * numOfFiles);
		if (encrypted)
			requiredBits += Crypto.GCM_AAD_SIZE + Crypto.AES_IV_SIZE + Crypto.SALT_SIZE_BITS;

		int maxCapacity = img.getWidth() * img.getHeight() * (img.getColorModel().hasAlpha() ? 4 : 3) * LSBsToUse;

		if (requiredBits > maxCapacity) {
			System.err.println("Not enough space in image, consider allowing more bits or using a larger image");
			System.err.println("Required capacity: " + requiredBits);
			System.err.println("Bits that can be encoded: " + maxCapacity);
			System.out.println();
			return false;
		}

		return true;
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
	 * specified when instantiating the object this method is called from. Through the use of the threaded encoder,
	 * a job is submitted to an available thread, and the end positions of the encoding process are returned. The job
	 * will be carried out in parallel.
	 *
	 * @param bitsToEncode Array containing only bits, that are to be encoded in the image
	 */

	public void encodeBits(@NotNull byte[] bitsToEncode) {
		currBit = 0;
		for (; currBit < bitsToEncode.length; ) {
			for (; currBit < bitsToEncode.length; ) {
				img.setRGB(x, y, insertDataToPixel(img.getRGB(x, y), bitsToEncode));

				// If all data has been read from the pixel, move to next one
				if ((currLSB %= LSBsToUse) == 0 && nextChanToWrite == 0) {
					if (++x == width) {
						x = 0;
						y++;
					}
				}
			}
		}
	}

	private int insertDataToPixel(int orig, byte[] bitsToWrite) {
		byte[] aBits = null;
		if (numOfChannels == 4)
			aBits = BitByteConv.intToBitArray(((orig >> 24) & 0xff), Byte.SIZE); // Get original alpha bits
		byte[] rBits = BitByteConv.intToBitArray(((orig >> 16) & 0xff), Byte.SIZE); // Get original red bits
		byte[] gBits = BitByteConv.intToBitArray(((orig >> 8) & 0xff), Byte.SIZE); // Get original green bits
		byte[] bBits = BitByteConv.intToBitArray(orig & 0xff, Byte.SIZE); // Get original blue bits

		// Mod bit values, in order to encode bits from the buffer. Read method doc for more info
		for (; currLSB < LSBsToUse && currBit < bitsToWrite.length; ) {
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

	/**
	 * Encodes an array of bytes into the image, by breaking chunks of bytes into a bit array, and using
	 * encodeBits(byte[] bitsToEncode) in order to carry out the encoding of that bit array.
	 *
	 * @param bytesToEncode Array of bytes to be encoded in the image
	 */
	public void encodeBytes(@NotNull byte[] bytesToEncode) {
		int currByte = 0;
		int remainingBytes = bytesToEncode.length;
		int approxBytesPerThread = bytesToEncode.length / encThreads.length + 1;
		while (remainingBytes > 0) {
			// Number of bytes that the thread should write to the file byte array
			approxBytesPerThread = approxBytesPerThread < remainingBytes ? approxBytesPerThread : remainingBytes;

			for (int t = 0; t < encThreads.length; t = (t + 1) % encThreads.length) {
				if (!encThreads[t].isActive()) {
					ImgEndState endState = encThreads[t].submitJob(bytesToEncode, currByte, approxBytesPerThread, x,
																   y, currLSB, nextChanToWrite);
					x = endState.endX;
					y = endState.endY;
					currLSB = endState.endLSB;
					nextChanToWrite = endState.endChan;
					break;
				} else if (t + 1 == encThreads.length)
					sleep(5);
			}

			currByte += approxBytesPerThread;
			remainingBytes -= approxBytesPerThread;
		}

//		for (byte b : bytesToEncode)
//			encodeBits(BitByteConv.intToBitArray(b, Byte.SIZE));
	}

	/**
	 * Initializes the threads and encodes the least significant bits to be used during the encoding process.
	 * Once this method returns, the threads are ready to encode.
	 *
	 * @param numOfChannels Number of channels in the image
	 * @param LSBsToUse     Number of least significant bits to use for encoding the data
	 */
	private void initThreads(int numOfChannels, int LSBsToUse) {
		for (int i = 0; i < encThreads.length; i++) {
			encThreads[i] = new ImgEncoderThread(img, numOfChannels, i);
			encThreads[i].start();
		}

		encodeBits(BitByteConv.intToBitArray(LSBsToUse, 4));

//		for (int t = 0; t < encThreads.length; ) {
//			if (!encThreads[t].isActive())
//				t++;
//			else
//				sleep(10);
//		}

		for (ImgEncoderThread t : encThreads)
			t.setLSBsToUse(LSBsToUse);
	}

	/**
	 * Stops all ImgEncoderThread instances. Must be called once this ImgEncoder instance has finished writing data,
	 * otherwise the encoding will not complete successfully.
	 */
	public void stopThreads() {
		for (ImgEncoderThread t : encThreads) t.stopThread();
	}
}