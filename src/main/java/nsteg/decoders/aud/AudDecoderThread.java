package nsteg.decoders.aud;

import nsteg.encoders.aud.AudEndState;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.threads.AudThread;

import javax.validation.constraints.NotNull;

/**
 * Used to decode data from a PCM byte array which was previously encoded using AudEncoder. These threads are managed
 * by AudDecoder, but are only used in the encodeBytes method, since it is meant for handling larger volumes of data,
 * such as decoding files.
 */
public class AudDecoderThread extends AudThread {
	private byte[] byteArr; // Byte array that will hold the assembled byte array that all instances of this thread work on
	private int currFileByte, endByte;

	/**
	 * Initializes the thread, and all the variables in the superclasses.
	 *
	 * @param pcm Byte array of PCM audio data that contains the data to be decoded
	 */
	AudDecoderThread(@NotNull byte[] pcm) {
		super(pcm);
	}

	/**
	 * Submits a decoding job for this thread to carry out. The thread will carry it out immediately, and the calling
	 * thread is free to keep submitting jobs with the AudEndState instance returned by this method, since it gives the
	 * positions this thread will end at, which is where the next thread should start.
	 *
	 * @param byteArr      Array to which the threads working on the current decoding task will write to. This array will
	 *                     contain the decoded data
	 * @param currDecByte  Position in the PCM byte array to start decoding data from
	 * @param currFileByte Position in the destination byte array to start writing the decoded data to
	 * @param currLSB      Least significant bit to start decoding from
	 * @param bytesToRead  Number of bytes to decode from the PCM byte array and to write to the destination byte array
	 * @return AudEndState instance containing where the decoding job this thread has to carry out will end. This is
	 * used by AudDecoder to submit jobs to other threads without having to wait for this one to finish
	 */
	AudEndState submitJob(@NotNull byte[] byteArr, int currDecByte, int currFileByte, int currLSB, int bytesToRead) {
		this.byteArr = byteArr;
		this.currPCMByte = currDecByte;
		this.currFileByte = currFileByte;
		this.currLSB = currLSB;
		this.endByte = currFileByte + bytesToRead;

		active = true;

		int bitsToRead = bytesToRead * Byte.SIZE;
		/*
		 * If decoding starts from a partially read PCM byte, subtract the bits left in the byte in order to correctly
		 * calculate the endByte and end least significant bit, otherwise the endState values may be slightly off.
		 */
		if (currLSB > 0)
			bitsToRead -= (LSBsToUse - (currLSB));
		endState.endByte = currPCMByte + 2 * (bitsToRead / LSBsToUse);
		endState.endLSB = bitsToRead % LSBsToUse;
		/*
		 * If decoding starts from a partially read PCM byte, the end byte will be off by two, so correct that.
		 * TODO: THIS HONESTLY NEEDS IMPROVING, WORK OUT WITH PAPER BECAUSE IT'S A BIT ODD
		 */
		if (currLSB > 0)
			endState.endByte += 2;

		return endState;
	}

	@Override
	public void run() {
		while (running) {
			if (active) {
				int bitPos = 0;
				byte[] fileBits = new byte[Byte.SIZE];
				while (currFileByte < endByte && currPCMByte < pcm.length) {

					byte[] bits = BitByteConv.intToBitArray(pcm[currPCMByte], Byte.SIZE);
					for (; currLSB < LSBsToUse && currFileByte < endByte; currLSB++) {
						// If byte has been read in to buffer, assemble it and set it in the byte array
						if (bitPos == Byte.SIZE) {
							byteArr[currFileByte++] = (byte) BitByteConv.bitArrayToInt(fileBits, true);
							bitPos = 0;
						}
						fileBits[bitPos++] = bits[bits.length - 1 - currLSB];
					}

					if (currLSB == LSBsToUse) {
						currLSB = 0;
						currPCMByte += 2; // Skip the left channel byte, and go to the next right channel byte
					}
				}

				active = false;
			} else
				sleepMillis(10);
		}
	}
}
