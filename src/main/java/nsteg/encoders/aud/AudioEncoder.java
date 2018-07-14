package nsteg.encoders.aud;

import nsteg.processors.AudioProcessor;
import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Crypto;

import javax.sound.sampled.AudioInputStream;

public class AudioEncoder extends Encoder {
	private byte[] decBytes;

	private int currLSB = 0, currByte = 0;
	private int LSBsToUse = 1;

	public AudioEncoder(AudioInputStream audFile, int LSBsToUse) {
		decBytes = AudioProcessor.loadAudioFile(audFile);

		encodeBits(BitByteConv.intToBitArray(LSBsToUse, LEAST_SIG_BITS_TO_USE));
		this.LSBsToUse = LSBsToUse;
	}

	public byte[] getEncodedPCM() {
		return decBytes;
	}

	public void encodeBits(byte[] bits) {
		int bitPos = 0;
		for (; currByte < decBytes.length && bitPos < bits.length; ) {
			byte[] byteBits = BitByteConv.intToBitArray(decBytes[currByte], Byte.SIZE);
			for (; currLSB < LSBsToUse && bitPos < bits.length; currLSB++)
				byteBits[byteBits.length - 1 - currLSB] = bits[bitPos++];
			decBytes[currByte] = (byte) BitByteConv.bitArrayToInt(byteBits, true);

			if (currLSB == LSBsToUse) {
				currLSB = 0;
				currByte += 2;
			}
		}
	}

	public void encodeBytes(byte[] bytesToEncode) {
		for (byte b : bytesToEncode)
			encodeBits(BitByteConv.intToBitArray(b, Byte.SIZE));
	}

	public boolean doesFileFit(int fileSizeInBits, int LSBsToUse, boolean encrypted) {
		long requiredBits = LEAST_SIG_BITS_TO_USE + (2 * SIZE_BITS_COUNT) + fileSizeInBits;
		if (encrypted)
			requiredBits += Crypto.GCM_AAD_SIZE + Crypto.AES_IV_SIZE + Crypto.SALT_SIZE_BITS;

		int maxCapacity = ((decBytes.length * Byte.SIZE) / 2) * LSBsToUse;

		if (requiredBits > maxCapacity) {
			System.err.println("Audio file not long enough, consider allowing more bits or using another audio file");
			System.err.println("Required capacity: " + requiredBits);
			System.err.println("Bits that can be encoded: " + maxCapacity);
			System.out.println();
			return false;
		}

		return true;
	}

	public void stopThreads() {

	}
}
