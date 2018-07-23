package nsteg.encoders.aud;

import nsteg.nsteg_utils.BitByteConv;

/**
 * This class operates on an audio file to encode data passed on by AudioEncoder. It does so in a way that allows
 * threaded encoding, in order to use system resources fully. In essence, AudioEncoder submits a job, and this class
 * tells it where it will finish encoding the passed data, so that AudioEncoder can then assign more jobs to other
 * instances of this class.
 */
public class AudEncoderThread extends Thread {
	private AudEndState audEndState = new AudEndState();

	private boolean running = true; // Only set to false once threads are done all their work, and no more will come
	private boolean active = false; // Used to determine if the thread is working or waiting for work

	private static int LSBsToUse = 1;
	private int currByte, currLSB;

	private byte[] pcm, bitsToEncode;

	/**
	 * Initializes the thread, and assigns the PCM byte array that will be worked on.
	 *
	 * @param pcm PCM byte array into which this thread should encode
	 */
	AudEncoderThread(byte[] pcm) {
		this.setDaemon(true);

		this.pcm = pcm;
	}

	/**
	 * Sets the number of least significant bits for all instances to use.
	 *
	 * @param LSBToUse Number of least significant bits to use on each right channel byte. More allows for more data to
	 *                 be stored, but will cause greater distortion. 1-4 recommended.
	 */
	static void setLSBToUse(int LSBToUse) {
		AudEncoderThread.LSBsToUse = LSBToUse;
	}

	/**
	 * Causes the thread to exit, once the latest job submitted has been finished.
	 */
	void stopRunning() {
		running = false;
	}

	/**
	 * Returns whether a thread is currently encoding data.
	 *
	 * @return True if thread is encoding data, false otherwise
	 */
	boolean isActive() {
		return active;
	}

	/**
	 * Submits a job for this thread to do. It will return the position the encoding process will end at, so that jobs
	 * can be submitted to other threads without having to wait for this one to finish.
	 *
	 * @param startByte    Position in the PCM byte array to start at (should always be even, since even byte positions
	 *                     correspond to the right channel)
	 * @param startLSB     Least significant bit to start at in the startByte
	 * @param bitsToEncode Array of bits to encode into the PCM byte array
	 * @return AudEndState object, containing the ending byte and ending LSB, so that more jobs can be submitted while
	 * this one is being carried out
	 */
	AudEndState submitJob(int startByte, int startLSB, byte[] bitsToEncode) {
		this.currByte = startByte;
		this.currLSB = startLSB;
		this.bitsToEncode = bitsToEncode;

		active = true;

		// Least significant bit the encoding process will end at, given the job
		audEndState.endLSB = (startLSB + bitsToEncode.length) % LSBsToUse;
		// Byte the encoding process will end at, given the job. Left channel bits are skipped, so only every second byte is touched
		audEndState.endByte = startByte + (2 * ((startLSB + bitsToEncode.length) / LSBsToUse));

		return audEndState;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				for (int currBitPos = 0; currBitPos < bitsToEncode.length; ) {
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
