package nsteg.decoders.img;

import nsteg.decoders.Decoder;
import nsteg.encoders.img.ImgEncoder;
import nsteg.encoders.img.ImgEndState;
import nsteg.nsteg_utils.BitByteConv;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * This class serves to decode data from an image that was previously encoded using the ImgEncoder class. It reads
 * from the least significant bits from each channel, in each pixel, in the image. The number of LSBs is retrieved from
 * the beginning of the image, where the number is encoded using only 1 LSB. See ImgEncoder for the specification of how
 * the data is encoded.
 * <p><br>
 * The data is decoded sequentially, meaning that it must be decoded in the same order it was encoded, since that is
 * the way it was initially encoded by the Encoder class.
 * <p><br>
 * A nibble is required to store the number of LSBs in each channel that will be used for encoding, and subsequently,
 * decoding. In the event the image being worked on has three channels (RGB), the first two pixels are reserved for
 * storing this nibble, which means two channels in the second pixel go unused. So after writing the number of LSBs per
 * channel, values are reset to start encoding at the third pixel, as if no data had been written yet. If the image has
 * four channels (ARGB), only the first pixel is used for encoding, and data encoding starts at the second pixel, which
 * means no space is wasted.
 *
 * @see nsteg.encoders.Encoder
 */
public class ImgDecoder extends Decoder {
	private ImgDecoderThread[] decThreads = new ImgDecoderThread[Runtime.getRuntime().availableProcessors()];

	private BufferedImage img; // Image to read (A)RGB data from and to write (A)RGB modified data to
	private int x = 0, y = 0; // Pixel coords
	private int width, height; // Img dims

	private int bitsPerChannel = 1; // Number of LSBs to use in each channel for encoding purposes
	private final int numOfChannels;
	private int currByte = 0;

	// Bits read from pixels are loaded to the buffer, for temporary storage, until the requested amount of them have been read
	private ArrayDeque<Byte> buffer = new ArrayDeque<>();

	private long totTime = 0;

	/**
	 * Initializes an ImgDecoder instance with the given image, determines the number of channels in the image, and
	 * decodes the number of LSBs that were used when encoding the image.
	 *
	 * @param encImg Image with data to be decoded
	 */
	public ImgDecoder(@NotNull BufferedImage encImg) {
		img = encImg;
		numOfChannels = img.getColorModel().hasAlpha() ? 4 : 3;
		width = img.getWidth();
		height = img.getHeight();

		/*
		 * Read the number of LSBs that were used when encoding data to this image. Values are read from the first pixel
		 * if the image is of ARGB type, or the first two pixels, if the image is of type RGB, since this value is
		 * encoded using only one LSB.
		 */

		initThreads();
	}

	/**
	 * Decodes bits from the image, until there are enough in the buffer to return the requested number of bits.
	 * <p>
	 * Reads one pixel at a time, and adds all the decoded bits to the buffer, until the buffer has enough bits
	 * to return them. Any excess bits read will stay in the buffer, as they may belong to the next block of bits, if
	 * there is a next block.
	 *
	 * @param bitsToRead Desired number of bits to decode and return
	 * @return Array of bits with requested number of bits
	 */
	public byte[] readBits(int bitsToRead) {
		for (; y < height; y++) {
			for (; x < width; ) {
//				long start = System.currentTimeMillis();
				while (buffer.size() < bitsToRead && x < width)
					extractDataFromPixel(img.getRGB(x++, y));
//				totTime += (System.currentTimeMillis() - start);

				if (buffer.size() >= bitsToRead)
					return loadFromBuffer(bitsToRead);
			}
			x = 0;
		}

		System.err.println("No more bits to read...");
		return null;
	}

	/**
	 * Reads bytes from the image, and returns them as a byte array. Uses readBits(byte[] bitsToRead) internally.
	 *
	 * @param bytesToRead Number of bytes to decode from the image
	 * @return Array of decoded bytes
	 */
	public byte[] readBytes(int bytesToRead) {
		byte[] extractedBytes = new byte[bytesToRead];

//		for (int i = 0; i < bytesToRead; i++)
//			extractedBytes[i] = (byte) BitByteConv.bitArrayToInt(readBits(Byte.SIZE), true);

//		System.out.println(totTime);

		currByte = 0;
		int remainingBytes = bytesToRead;
		int approxBytesPerThread = bytesToRead / decThreads.length + 1;
		for (int t = 0; t < decThreads.length; t++) {
			approxBytesPerThread = approxBytesPerThread < remainingBytes ? approxBytesPerThread : remainingBytes;
			ImgEndState endState = decThreads[t].submitJob(extractedBytes, buffer, x, y, approxBytesPerThread, currByte);
			currByte += approxBytesPerThread;
			x = endState.endX;
			y = endState.endY;

			if (endState.endLSB > 0) {
				extractDataFromPixel(img.getRGB(x, y));
				while (buffer.size() > endState.endLSB)
					buffer.removeFirst();
			}

			remainingBytes -= approxBytesPerThread;
		}

		stopThreads();

		return extractedBytes;
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

	private void initThreads() {
		for (int t = 0; t < decThreads.length; t++) {
			decThreads[t] = new ImgDecoderThread(img, numOfChannels);
			decThreads[t].start();
		}

		bitsPerChannel = BitByteConv.bitArrayToInt(readBits(4), false);
		buffer.clear();
		ImgDecoderThread.setBitsPerChannel(bitsPerChannel);
	}

	public void stopThreads() {
		for (int t = 0; t < decThreads.length; ) {
			if (!decThreads[t].isActive()) {
				decThreads[t].stopRunning();
				try {
					decThreads[t].join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				t++;
			} else
				ImgEncoder.sleep(1);
		}
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