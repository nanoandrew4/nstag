package nsteg.decoders.aud;

import nsteg.encoders.aud.AudEndState;
import nsteg.nsteg_utils.BitByteConv;

import javax.validation.constraints.NotNull;

public class AudDecoderThread extends Thread {
	private AudEndState endState = new AudEndState();
	private byte[] pcm;

	private boolean running = true;
	private boolean active = false;

	private byte[] fileBytes;
	private static int LSBsToUse;
	private int currLSB, currDecByte, currFileByte, endByte;

	AudDecoderThread(@NotNull byte[] pcm) {
		this.setDaemon(true);

		this.pcm = pcm;
	}

	static void setLSBsToUse(int LSBsToUse) {
		AudDecoderThread.LSBsToUse = LSBsToUse;
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

	AudEndState submitJob(@NotNull byte[] fileBytes, int currDecByte, int currFileByte, int currLSB, int bytesToRead) {
		this.fileBytes = fileBytes;
		this.currDecByte = currDecByte;
		this.currFileByte = currFileByte;
		this.currLSB = currLSB;
		this.endByte = currFileByte + bytesToRead;

		int bitsToRead = (bytesToRead * Byte.SIZE);
		if (currLSB > 0)
			bitsToRead -= (LSBsToUse - (currLSB));
		endState.endByte = currDecByte + 2 * (bitsToRead / LSBsToUse);
		endState.endLSB = bitsToRead % LSBsToUse;
		if (currLSB > 0)
			endState.endByte += 2;

		active = true;
		return endState;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				int bitPos = 0;
				byte[] fileBits = new byte[Byte.SIZE];
				while (currFileByte < endByte && currDecByte < pcm.length) {
					byte[] bits = BitByteConv.intToBitArray(pcm[currDecByte], Byte.SIZE);
					for (; currLSB < LSBsToUse && currFileByte < endByte && currDecByte < pcm.length; currLSB++) {
						if (bitPos == Byte.SIZE) {
							fileBytes[currFileByte++] = (byte) BitByteConv.bitArrayToInt(fileBits, true);
							bitPos = 0;
						}
						fileBits[bitPos++] = bits[bits.length - 1 - currLSB];
					}

					if (currLSB == LSBsToUse) {
						currLSB = 0;
						currDecByte += 2;
					}
				}

				active = false;
			} else
				AudioDecoder.sleep(1);
		}
	}
}
