package nsteg.decoders.aud;

import nsteg.encoders.aud.AudEndState;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.threads.AudThread;

import javax.validation.constraints.NotNull;

public class AudDecoderThread extends AudThread {
	private byte[] fileBytes;
	private int currFileByte, endByte;

	AudDecoderThread(@NotNull byte[] pcm) {
		super(pcm);
	}

	AudEndState submitJob(@NotNull byte[] fileBytes, int currDecByte, int currFileByte, int currLSB, int bytesToRead) {
		this.fileBytes = fileBytes;
		this.currPCMByte = currDecByte;
		this.currFileByte = currFileByte;
		this.currLSB = currLSB;
		this.endByte = currFileByte + bytesToRead;

		active = true;

		int bitsToRead = (bytesToRead * Byte.SIZE);
		if (currLSB > 0)
			bitsToRead -= (LSBsToUse - (currLSB));
		endState.endByte = currPCMByte + 2 * (bitsToRead / LSBsToUse);
		endState.endLSB = bitsToRead % LSBsToUse;
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
					for (; currLSB < LSBsToUse && currFileByte < endByte && currPCMByte < pcm.length; currLSB++) {
						if (bitPos == Byte.SIZE) {
							fileBytes[currFileByte++] = (byte) BitByteConv.bitArrayToInt(fileBits, true);
							bitPos = 0;
						}
						fileBits[bitPos++] = bits[bits.length - 1 - currLSB];
					}

					if (currLSB == LSBsToUse) {
						currLSB = 0;
						currPCMByte += 2;
					}
				}

				active = false;
			} else
				sleepMillis(5);
		}
	}
}
