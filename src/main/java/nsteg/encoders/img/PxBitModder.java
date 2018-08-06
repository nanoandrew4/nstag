package nsteg.encoders.img;

import nsteg.nsteg_utils.BitByteConv;

/**
 * Carries out pixel color modification in order to encode data in the least significant bits of each channel. Given
 * a 32 bit integer representing the color values for the 4 channels (8 bits for each channel, and even if the image
 * is RGB, the alpha channel is passed, but ignored when writing to the image), and an array of bits, this class
 * will insert the bits into the least significant bits of the pixel, and return the modified 32 bit integer, through
 * the use of the insertDataToPixel() method. This class is employed by ImgEncoder and ImgEncoderThread.
 *
 * @see ImgEncoder
 * @see ImgEncoderThread
 */
public class PxBitModder {
	private int LSBsToUse;
	private int currBit = 0, currLSB, nextChanToWrite;
	private boolean hasAlphaChan;

	/**
	 * Initializes a pixel bit modifier instance with the given values.
	 *
	 * @param numOfChannels Number of channels that should be written to, which is equal to the number of channels
	 *                      that the image being modified has. Used to determine
	 * @param LSBsToUse     Number of least significant bits to use for inserting bits in each channel
	 * @param startLSB      Least significant bit to start writing at
	 * @param startChan     Channel to start writing at
	 */
	PxBitModder(int numOfChannels, int LSBsToUse, int startLSB, int startChan) {
		this.hasAlphaChan = numOfChannels == 4;
		this.LSBsToUse = LSBsToUse;
		this.currLSB = startLSB;
		this.nextChanToWrite = startChan;
	}

	int getCurrLSB() {
		return currLSB;
	}

	/**
	 * Sets the least significant bit that insertDataToPixel() will start inserting from, next time it is called.
	 *
	 * @param currLSB LSB to start working from when insertDataToPixel() is called next
	 */
	void setCurrLSB(int currLSB) {
		this.currLSB = currLSB;
	}

	void setNextChanToWrite(int nextChanToWrite) {
		this.nextChanToWrite = nextChanToWrite;
	}

	int getNextChanToWrite() {
		return nextChanToWrite;
	}

	/**
	 * Sets the position that the array passed to insertDataToPixel() will be read from to 0. Should be used after an
	 * array has been fully inserted (when currBit == bitsToWrite.length).
	 */
	void resetCurrBit() {
		this.currBit = 0;
	}

	int getCurrBit() {
		return currBit;
	}

	void setLSBsToUse(int LSBsToUse) {
		this.LSBsToUse = LSBsToUse;
	}

	int getLSBsToUse() {
		return LSBsToUse;
	}

	/**
	 * Writes bits to the various LSBs in the various channels the current pixel being worked on has. Since the whole
	 * pixel is not likely to be used in one pass, this method remembers where it left off, so that all the data is
	 * encoded sequentially, which allows speedy decoding and for maximum data density.
	 *
	 * @param orig        Original 32-bit argb int representing the color of the pixel
	 * @param bitsToWrite Array of bits that should be inserted into the least significant bits of each channel
	 * @return Modified 32-bit argb int representing the new color of the pixel, which contains the inserted bits
	 */
	int insertDataToPixel(int orig, byte[] bitsToWrite) {
		byte[] aBits = null;
		if (hasAlphaChan)
			aBits = BitByteConv.intToBitArray(((orig >> 24) & 0xff), Byte.SIZE); // Get original alpha bits
		byte[] rBits = BitByteConv.intToBitArray(((orig >> 16) & 0xff), Byte.SIZE); // Get original red bits
		byte[] gBits = BitByteConv.intToBitArray(((orig >> 8) & 0xff), Byte.SIZE); // Get original green bits
		byte[] bBits = BitByteConv.intToBitArray(orig & 0xff, Byte.SIZE); // Get original blue bits
		// Mod bit values, in order to encode bitsToWrite from the buffer. Read method doc for more info
		for (; currLSB < LSBsToUse && currBit < bitsToWrite.length; ) {
			if (nextChanToWrite == 0) {
				rBits[rBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				nextChanToWrite = 1;
			}

			if (currBit < bitsToWrite.length && nextChanToWrite == 1) {
				gBits[gBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				nextChanToWrite = 2;
			}

			if (currBit < bitsToWrite.length && nextChanToWrite == 2) {
				bBits[bBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				if (!hasAlphaChan) {
					currLSB++;
					nextChanToWrite = 0;
					continue;
				} else
					nextChanToWrite = 3;
			}

			if (nextChanToWrite == 3 && currBit < bitsToWrite.length) {
				aBits[aBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				nextChanToWrite = 0;
				currLSB++;
			}
		}

		// Return 32-bit int representing the color of the pixel, with the encoded bits
		if (!hasAlphaChan)
			return (BitByteConv.bitArrayToInt(rBits, false) << 16) |
				   (BitByteConv.bitArrayToInt(gBits, false) << 8) | BitByteConv.bitArrayToInt(bBits, false);
		else
			return (BitByteConv.bitArrayToInt(aBits, false) << 24) | (BitByteConv.bitArrayToInt(rBits, false) << 16)
				   | (BitByteConv.bitArrayToInt(gBits, false) << 8) | BitByteConv.bitArrayToInt(bBits, false);
	}
}
