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

		int bitsToRead = bytesToRead * Byte.SIZE - (LSBsToUse - currLSB - 1);
		endState.endByte = currDecByte + 2 * (bitsToRead / LSBsToUse);
		endState.endLSB = bitsToRead % LSBsToUse;

		active = true;
		return endState;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				int bitPos = 0;
				byte[] fileBits = new byte[Byte.SIZE];
				while (currFileByte < endByte) {
					byte[] bits = BitByteConv.intToBitArray(pcm[currDecByte++], Byte.SIZE);
					for (int i = currLSB; currLSB < LSBsToUse; currLSB++) {
						if (bitPos >= Byte.SIZE) {
							fileBytes[currFileByte++] = (byte) BitByteConv.bitArrayToInt(fileBits, true);
							bitPos %= Byte.SIZE;
						}
						fileBits[bitPos++] = bits[bits.length - 1 - i];
					}
				}
			} else
				AudioDecoder.sleep(1);
		}
	}
}
