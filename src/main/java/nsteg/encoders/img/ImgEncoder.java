package nsteg.encoders.img;

import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Crypto;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;

/**
 * This class will encode data to the specified image, using the specified number of least significant bit(s) in each
 * pixel to do so. It writes all data sequentially, so it should be decoded in that same order it was encoded, later on.
 * For the encoding spec, see the Encoder class.
 * <p><br>
 * A nibble is required to store the number of LSBs in each channel that will be used for encoding, and subsequently,
 * decoding. In the event the image being worked on has three channels (RGB), the first two pixels are reserved for
 * storing this nibble, which means two channels in the second pixel go unused. So after writing the number of LSBs per
 * channel, values are reset to start encoding at the third pixel, as if no data had been written yet. If the image has
 * four channels (ARGB), only the first pixel is used for encoding, and data encoding starts at the second pixel, which
 * means no space is wasted.
 *
 * @see Encoder
 */
public class ImgEncoder extends Encoder {
	private PxBitModder bitModder; // For use by this class, to encode bits
	private ImgEncoderThread[] encThreads = new ImgEncoderThread[Runtime.getRuntime().availableProcessors()];

	private BufferedImage img; // Image to read (A)RGB data from and to write (A)RGB modified data to
	private int x = 0, y = 0; // Current pixel coordinates

	/**
	 * Initializes an ImgEncoder instance with the given image, determines the number of channels in the image,
	 * and encodes the number of LSBs that will be used in each channel into the image.
	 *
	 * @param origImg   Image to encode data into
	 * @param LSBsToUse Number of least significant bits to use in each channel
	 */
	public ImgEncoder(@NotNull BufferedImage origImg, int LSBsToUse) {
		img = origImg;
		int numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;

		bitModder = new PxBitModder(numOfChannels, 1, 0, 0);
		encodeBits(BitByteConv.intToBitArray(LSBsToUse, 4));

		initThreads(numOfChannels, LSBsToUse);
		bitModder.setLSBsToUse(LSBsToUse);

		// Use two pixels for LSBsToUse encoding, so restart encoding at 3rd pixel, if image only has three channels
		if (numOfChannels == 3) {
			x = 2;
			bitModder.setCurrLSB(0);
			bitModder.setNextChanToWrite(0);
		}
	}

	// See abstract method declaration
	public boolean doesFileFit(int fileSizeInBits, int numOfFiles, int fileNameLengths, int LSBsToUse, boolean
			encrypted) {
		long requiredBits = LSB_BITS_COUNT + SIZE_BITS_COUNT + fileSizeInBits + SIZE_BITS_COUNT +
							(SIZE_BITS_COUNT * numOfFiles) * 2 + fileNameLengths * Byte.SIZE;
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
		bitModder.resetCurrBit();
		while (bitModder.getCurrBit() < bitsToEncode.length) {
			img.setRGB(x, y, bitModder.insertDataToPixel(img.getRGB(x, y), bitsToEncode));

			// If all data has been read from the pixel, move to next one
			if (bitModder.getCurrLSB() == bitModder.getLSBsToUse()) {
				bitModder.setCurrLSB(0);
				if (++x == img.getWidth()) {
					x = 0;
					y++;
				}
			}
		}
	}

	/**
	 * Encodes an array of bytes into the image, breaking the bytes to write into chunks, and passing each chunk to a
	 * thread for it to be carried out in parallel. This method returning does not ensure that the data has fully been
	 * written to the image. After all that is to be encoded has been submitted, stopThreads() must be called in order
	 * to wait for the threads to finish their work, and then shut them down. Once stopThreads() returns, the data
	 * has been fully written to the image.
	 *
	 * @param bytesToEncode Array of bytes to be encoded in the image
	 */
	public void encodeBytes(@NotNull byte[] bytesToEncode) {
		int currByte = 0;
		int remainingBytes = bytesToEncode.length;
		int approxBytesPerThread = bytesToEncode.length / encThreads.length;
		while (remainingBytes > 0) {
			// Number of bytes that the thread should write to the file byte array
			approxBytesPerThread = approxBytesPerThread < remainingBytes ? approxBytesPerThread : remainingBytes;

			for (int t = 0; t < encThreads.length; t = (t + 1) % encThreads.length) {
				if (!encThreads[t].isActive()) {
					ImgEndState endState = encThreads[t].submitJob(
							bytesToEncode, currByte, approxBytesPerThread, x, y, bitModder.getCurrLSB(),
							bitModder.getNextChanToWrite()
					);

					/*
					 * The job submission will return where the job will end encoding, so that the next thread knows
					 * where to start. The bit modder is updated too, in the event that more bits should be written
					 * after this method finishes.
					 */
					x = endState.endX;
					y = endState.endY;
					bitModder.setCurrLSB(endState.endLSB);
					bitModder.setNextChanToWrite(endState.endChan);
					break;
				} else if (t + 1 == encThreads.length)
					sleep(5);
			}

			currByte += approxBytesPerThread;
			remainingBytes -= approxBytesPerThread;
		}
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
			encThreads[i].setLSBsToUse(LSBsToUse);
		}
	}

	/**
	 * Stops all ImgEncoderThread instances. Must be called once this ImgEncoder instance has finished writing data,
	 * otherwise the encoding may not complete successfully.
	 */
	public void stopThreads() {
		for (ImgEncoderThread t : encThreads) t.stopThread();
	}
}