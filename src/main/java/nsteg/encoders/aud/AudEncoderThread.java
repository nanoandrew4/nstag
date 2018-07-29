package nsteg.encoders.aud;

import nsteg.nsteg_utils.BitByteConv;
import nsteg.threads.AudThread;

/**
 * This class operates on an audio file to encode data passed on by AudEncoder. It does so in a way that allows
 * threaded encoding, in order to use system resources fully. In essence, AudEncoder submits a job, and this class
 * tells it where it will finish encoding the passed data, so that AudEncoder can then assign more jobs to other
 * instances of this class. See the AudEncoder class docs for an overview of the encoding specification.
 */
public class AudEncoderThread extends AudThread {
	private byte[] bitsToEncode; // Stores the bits that the thread must encode, passed in through the submitJob() method

	private static int[] threadPCMPos = new int[Runtime.getRuntime().availableProcessors()];
	private int threadID;

	/**
	 * Initializes the thread, and assigns the PCM byte array that will be worked on. Also sets up the thread safety
	 * mechanism.
	 *
	 * @param pcm PCM byte array into which this thread should encode
	 */
	AudEncoderThread(byte[] pcm, int threadID) {
		super(pcm);
		this.threadID = threadID;

		for (int i = 0; i < threadPCMPos.length; i++)
			threadPCMPos[i] = -1;
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
		this.currPCMByte = startByte;
		this.currLSB = startLSB;
		this.bitsToEncode = bitsToEncode;

		active = true;

		// Least significant bit the encoding process will end at, given the job
		endState.endLSB = (startLSB + bitsToEncode.length) % LSBsToUse;
		// Byte the encoding process will end at, given the job. Left channel bits are skipped, so only every second byte is touched
		endState.endByte = startByte + (2 * ((startLSB + bitsToEncode.length) / LSBsToUse));

		return endState;
	}

	/*
	 * Prevents threads from modifying the same byte at the same time, since information might be lost.
	 * Returns false if the requested byte is being used by another thread, or returns true if the byte is available.
	 */
	private synchronized boolean requestLock(int PCMPos) {
		for (int i : threadPCMPos)
			if (i == PCMPos)
				return false;

		threadPCMPos[threadID] = PCMPos; // Set which byte is being modified by this thread
		return true;
	}

	/*
	 * Waits until the requested byte is available for modification.
	 */
	private void waitForLock(int PCMPos) {
		while (!requestLock(PCMPos))
			sleepMillis(1);
	}

	/*
	 * Releases the byte this thread was being worked on, so that another thread may work on the modified byte, instead
	 * of the original.
	 */
	private synchronized void release() {
		threadPCMPos[threadID] = -1;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				for (int currBitPos = 0; currBitPos < bitsToEncode.length; ) {
					waitForLock(currPCMByte);
					byte[] byteBits = BitByteConv.intToBitArray(pcm[currPCMByte], Byte.SIZE);

					// Write bits to the least significant bit(s), until no more bits can be written to current byte, or all bits have been written
					for (; currLSB < LSBsToUse && currBitPos < bitsToEncode.length; currLSB++)
						byteBits[byteBits.length - 1 - currLSB] = bitsToEncode[currBitPos++];
					pcm[currPCMByte] = (byte) BitByteConv.bitArrayToInt(byteBits, true);
					release();
					/*
					 * If no more bits to write in this byte, move on two bytes. This is done to prevent audible changes, since
					 * most music has two channels, and apparently changing the data in the second results in barely any
					 * perceptible distortion.
					 */
					if (currLSB == LSBsToUse) {
						currPCMByte += 2;
						currLSB = 0;
					}
				}

				active = false;
			} else
				sleepMillis(10);
		}
	}
}
