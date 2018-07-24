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
	private int x = 0, y = 0; // Pixel coords

	private int nextChanToWrite; // Next channel, at current LSB position in current pixel, to be written to
	private int currLSB; // Current least significant bit position, for all channels, at the current pixel

	/**
	 * Initializes an ImgEncoder instance with the given image, determines the number of channels in the image,
	 * and encodes the number of LSBs that will be used in each channel into the image.
	 *
	 * @param origImg        Image to encode data into
	 * @param bitsPerChannel Number of least significant bits to use in each channel
	 */
	public ImgEncoder(@NotNull BufferedImage origImg, int bitsPerChannel) {
		img = origImg;
		int numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;

		initThreads(numOfChannels, bitsPerChannel);

		// Use two pixels for bitsPerChannel encoding, so restart encoding at 3rd pixel, if image only has three channels
		if (numOfChannels == 3) {
			x = 2;
			currLSB = nextChanToWrite = 0;
		}

		ImgEncoderThread.setBitsPerChannel(bitsPerChannel);
	}

	// See abstract method declaration
	public boolean doesFileFit(int fileSizeInBits, int LSBsToUse, boolean encrypted) {
		long requiredBits = LSB_BITS_COUNT + (2 * SIZE_BITS_COUNT) + fileSizeInBits;
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
		for (int i = 0; i < encThreads.length; i %= encThreads.length) {
			if (!encThreads[i].isActive()) {
				ImgEndState imgEndState = encThreads[i].submitJob(bitsToEncode, x, y, currLSB, nextChanToWrite);
				x = imgEndState.endX;
				y = imgEndState.endY;
				currLSB = imgEndState.endLSB;
				nextChanToWrite = imgEndState.endChan;
				break;
			} else
				sleep(1);
			i++;
		}
	}

	/**
	 * Encodes an array of bytes into the image, by breaking chunks of bytes into a bit array, and using
	 * encodeBits(byte[] bitsToEncode) in order to carry out the encoding of that bit array.
	 *
	 * @param bytesToEncode Array of bytes to be encoded in the image
	 */
	public void encodeBytes(@NotNull byte[] bytesToEncode) {
		int currByte = 0;
		int bytesSize = bytesToEncode.length;
		for (; currByte < bytesToEncode.length; ) {
			int bytesPerThread = bytesToEncode.length / encThreads.length;
			bytesPerThread = (bytesSize < bytesPerThread ? bytesSize : bytesPerThread);
			byte[] bits = new byte[bytesPerThread * Byte.SIZE];
			for (int i = 0; i < bytesPerThread; i++)
				System.arraycopy(BitByteConv.intToBitArray(bytesToEncode[currByte++], Byte.SIZE), 0, bits, i * Byte.SIZE, Byte.SIZE);

			encodeBits(bits);

			bytesSize -= bytesPerThread;
		}
	}

	private void initThreads(int numOfChannels, int bitsPerChannel) {
		for (int i = 0; i < encThreads.length; i++) {
			encThreads[i] = new ImgEncoderThread(img, numOfChannels);
			encThreads[i].start();
		}

		// Encode desired LSBs per channel using only LSB of first (or first and second, depending on number of channels) pixel(s)
		ImgEncoderThread.setBitsPerChannel(1);
		encodeBits(BitByteConv.intToBitArray(bitsPerChannel, 4));

		while (encThreads[0].isActive())
			sleep(2);
	}

	/**
	 * Stops all ImgEncoderThread instances. Must be called once this ImgEncoder instance has finished writing data,
	 * otherwise the encoding will not complete successfully.
	 */
	public void stopThreads() {
		for (int i = 0; i < encThreads.length; ) {
			if (!encThreads[i].isActive()) {
				encThreads[i].stopRunning();
				try {
					encThreads[i].join();
				} catch (InterruptedException ignored) {
				}
				i++;
			} else
				sleep(1);
		}
	}
}