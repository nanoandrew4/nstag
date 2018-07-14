package nsteg.decoders.aud;

import nsteg.processors.AudioProcessor;
import nsteg.decoders.Decoder;
import nsteg.nsteg_utils.BitByteConv;

import javax.sound.sampled.AudioInputStream;

public class AudioDecoder extends Decoder {
	private byte[] decBytes;

	private int currLSB = 0, currByte = 0;
	private int LSBsToUse = 1;

	public AudioDecoder(AudioInputStream audFile) {
		decBytes = AudioProcessor.loadAudioFile(audFile);

		LSBsToUse = BitByteConv.bitArrayToInt(readBits(LEAST_SIG_BITS_TO_USE), false);
	}

	public byte[] readBits(int bitsToRead) {
		byte[] bits = new byte[bitsToRead];
		int bitPos = 0;

		for (; currByte < decBytes.length && bitPos < bitsToRead;) {
			byte[] byteBits = BitByteConv.intToBitArray(decBytes[currByte], Byte.SIZE);
			for (; currLSB < LSBsToUse && bitPos < bitsToRead; currLSB++)
				bits[bitPos++] = byteBits[byteBits.length - 1 - currLSB];
			if (currLSB == LSBsToUse) {
				currLSB = 0;
				currByte += 2;
			}
		}

		return bits;
	}

	public byte[] readBytes(int bytesToRead) {
		byte[] bytes = new byte[bytesToRead];

		for (int i = 0; i < bytesToRead; i++)
			bytes[i] = (byte) BitByteConv.bitArrayToInt(readBits(Byte.SIZE), true);

		return bytes;
	}
}
