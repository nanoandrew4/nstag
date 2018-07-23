package nsteg.encoders.aud;

import nsteg.nsteg_utils.BitByteConv;

public class AudEncoderThread extends Thread {
	private AudEndState audEndState = new AudEndState();

	private boolean running = true;
	private boolean active = false;

	private static int LSBsToUse = 1;
	private int currByte, currLSB;

	private byte[] pcm, bitsToEncode;

	AudEncoderThread(byte[] pcm) {
		this.setDaemon(true);

		this.pcm = pcm;
	}

	static void setLSBToUse(int LSBToUse) {
		AudEncoderThread.LSBsToUse = LSBToUse;
	}

	void stopRunning() {
		running = false;
	}

	boolean isActive() {
		return active;
	}

	AudEndState submitJob(int startByte, int startLSB, byte[] bitsToEncode) {
		this.currByte = startByte;
		this.currLSB = startLSB;
		this.bitsToEncode = bitsToEncode;

		active = true;

		audEndState.endLSB = (startLSB + bitsToEncode.length) % LSBsToUse;
		audEndState.endByte = startByte + (2 * ((startLSB + bitsToEncode.length) / LSBsToUse));

		return audEndState;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				int currBitPos = 0;
				while (currBitPos < bitsToEncode.length) {
					byte[] byteBits = BitByteConv.intToBitArray(pcm[currByte], Byte.SIZE);
					// Write bits to the least significant bit(s), until no more bits can be written to current byte, or all bits have been written
					for (; currLSB < LSBsToUse && currBitPos < bitsToEncode.length; currLSB++)
						byteBits[byteBits.length - 1 - currLSB] = bitsToEncode[currBitPos++];
					pcm[currByte] = (byte) BitByteConv.bitArrayToInt(byteBits, true);

					/*
					 * If no more bits to write in this byte, move on two bytes. This is done to prevent audible changes, since
					 * most music has two channels, and apparently changing the data in the second results in barely any
					 * perceptible distortion.
					 */
					if (currLSB == LSBsToUse) {
						currByte += 2;
						currLSB = 0;
					}
				}

				active = false;
			} else
				AudioEncoder.sleep(1);
		}
	}
}
