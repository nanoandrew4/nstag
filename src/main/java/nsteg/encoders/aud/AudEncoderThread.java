package nsteg.encoders.aud;

import nsteg.nsteg_utils.BitByteConv;
import nsteg.threads.AudThread;

/**
 * This class operates on an audio file to encode data passed on by AudEncoder. It does so in a way that allows
 * threaded encoding, in order to use system resources fully. In essence, AudEncoder submits a job, and this class
 * tells it where it will finish encoding the passed data, so that AudEncoder can then assign more jobs to other
 * instances of this class.
 */
public class AudEncoderThread extends AudThread {
	private byte[] bitsToEncode;

	/**
	 * Initializes the thread, and assigns the PCM byte array that will be worked on.
	 *
	 * @param pcm PCM byte array into which this thread should encode
	 */
	AudEncoderThread(byte[] pcm) {
		super(pcm);
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

	@Override
	public void run() {
		while (running) {
			if (active) {
				for (int currBitPos = 0; currBitPos < bitsToEncode.length; ) {
					byte[] byteBits = BitByteConv.intToBitArray(pcm[currPCMByte], Byte.SIZE);
					// Write bits to the least significant bit(s), until no more bits can be written to current byte, or all bits have been written
					for (; currLSB < LSBsToUse && currBitPos < bitsToEncode.length; currLSB++)
						byteBits[byteBits.length - 1 - currLSB] = bitsToEncode[currBitPos++];
					pcm[currPCMByte] = (byte) BitByteConv.bitArrayToInt(byteBits, true);

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
				sleepMillis(5);
		}
	}
}
