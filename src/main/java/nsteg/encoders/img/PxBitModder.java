package nsteg.encoders.img;

import nsteg.nsteg_utils.BitByteConv;

public class PxBitModder {
	private int numOfChannels, LSBsToUse;
	private int currBit = 0, currLSB, nextChanToWrite;

	PxBitModder(int numOfChannels, int LSBsToUse, int currLSB, int nextChanToWrite) {
		this.numOfChannels = numOfChannels;
		this.LSBsToUse = LSBsToUse;
		this.currLSB = currLSB;
		this.nextChanToWrite = nextChanToWrite;
	}

	public int getCurrLSB() {
		return currLSB;
	}

	public void setCurrLSB(int currLSB) {
		this.currLSB = currLSB;
	}

	public void setNextChanToWrite(int nextChanToWrite) {
		this.nextChanToWrite = nextChanToWrite;
	}

	public int getNextChanToWrite() {
		return nextChanToWrite;
	}

	public void resetCurrBit() {
		this.currBit = 0;
	}

	public int getCurrBit() {
		return currBit;
	}

	public void setLSBsToUse(int LSBsToUse) {
		this.LSBsToUse = LSBsToUse;
	}

	public int getLSBsToUse() {
		return LSBsToUse;
	}

	/**
	 * Writes bitsToWrite to the various LSBs in the various channels the current pixel being worked on has. Because
	 * RGB
	 * images
	 * have 3 channels, encoding data usually means that you will finish encoding before exhausting all the LSBs in all
	 * the channels of the last necessary pixel.
	 * <p><br>
	 * If one byte (8 bitsToWrite) were encoded in an RGB image, we would need 3 pixels to hold the 8 bitsToWrite
	 * (assuming we
	 * used one LSB), but would have one bit left, in which no data would be written, which would be a waste, aside
	 * from complicating the decoding process. Therefore, this method remembers where it left off, and will continue
	 * encoding at the next free LSB; in the case of the previous example, it will start writing whatever data is to
	 * be encoded next at the third channel of the third pixel, meaning that all the data is encoded sequentially,
	 * with no breaks. So efficient :D
	 *
	 * @param orig Original 32-bit argb int representing the color of the pixel
	 * @return Modified 32-bit argb int representing the new color of the pixel
	 */
	int insertDataToPixel(int orig, byte[] bitsToWrite) {
		byte[] aBits = null;
		if (numOfChannels == 4)
			aBits = BitByteConv.intToBitArray(((orig >> 24) & 0xff), Byte.SIZE); // Get original alpha bitsToWrite
		byte[] rBits = BitByteConv.intToBitArray(((orig >> 16) & 0xff), Byte.SIZE); // Get original red bitsToWrite
		byte[] gBits = BitByteConv.intToBitArray(((orig >> 8) & 0xff), Byte.SIZE); // Get original green bitsToWrite
		byte[] bBits = BitByteConv.intToBitArray(orig & 0xff, Byte.SIZE); // Get original blue bitsToWrite
		// Mod bit values, in order to encode bitsToWrite from the buffer. Read method doc for more info
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
				nextChanToWrite = numOfChannels == 3 ? 0 : 3;
				if (numOfChannels == 3) { // If no alpha channel, go on to next LSB, if possible
					currLSB++;
					continue;
				}
			} else if (currBit >= bitsToWrite.length)
				break;

			if (nextChanToWrite == 3 && currBit < bitsToWrite.length) {
				aBits[aBits.length - 1 - currLSB] = bitsToWrite[currBit++];
				nextChanToWrite = 0;
				currLSB++;
			}
		}

		// Return 32-bit int representing the color of the pixel, with the encoded bitsToWrite from the buffer
		if (numOfChannels == 3)
			return (BitByteConv.bitArrayToInt(rBits, false) << 16) |
				   (BitByteConv.bitArrayToInt(gBits, false) << 8) | BitByteConv.bitArrayToInt(bBits, false);
		else
			return (BitByteConv.bitArrayToInt(aBits, false) << 24) | (BitByteConv.bitArrayToInt(rBits, false) << 16)
				   | (BitByteConv.bitArrayToInt(gBits, false) << 8) | BitByteConv.bitArrayToInt(bBits, false);
	}
}
