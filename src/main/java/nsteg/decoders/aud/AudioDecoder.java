package nsteg.decoders.aud;

import nsteg.decoders.Decoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.processors.AudioProcessor;

import javax.sound.sampled.AudioInputStream;
import javax.validation.constraints.NotNull;

/**
 * This class is responsible for decoding data that was previously encoded using the AudioEncoder from the PCM data
 * of an audio file.
 */
public class AudioDecoder extends Decoder {
	private byte[] encodedBytes; // Holds the PCM data of the audio file

	private int currLSB = 0, currByte = 0;

	// Number of least significant bits to read from each byte of PCM data
	private int LSBsToUse = 1;

	public AudioDecoder(@NotNull AudioInputStream audFile) {
		encodedBytes = AudioProcessor.loadAudioFile(audFile);

		LSBsToUse = BitByteConv.bitArrayToInt(readBits(LSB_BITS_COUNT), false);
	}

	/**
	 * Decodes a specific number of bits from the PCM data, and returns them as an array.
	 *
	 * @param bitsToRead Number of bits to read
	 * @return Array containing the decoded bits, with the specified length
	 */
	public byte[] readBits(int bitsToRead) {
		byte[] bits = new byte[bitsToRead];
		int bitPos = 0;

		// Cycle through the decoded bytes, until enough bits have been gathered, or there are no more bytes to read from
		for (; currByte < encodedBytes.length && bitPos < bitsToRead; ) {
			byte[] byteBits = BitByteConv.intToBitArray(encodedBytes[currByte], Byte.SIZE);

			// Read the least significant bits until enough have been read, or no more left to read in current byte
			for (; currLSB < LSBsToUse && bitPos < bitsToRead; currLSB++)
				bits[bitPos++] = byteBits[byteBits.length - 1 - currLSB];

			/*
			 * If no more bits to read in this byte, move on two bytes. This is done to prevent audible changes, since
			 * most music has two channels, and apparently changing the data in the second results in barely any
			 * perceptible distortion.
			 */
			if (currLSB == LSBsToUse) {
				currLSB = 0;
				currByte += 2;
			}
		}

		return bits;
	}

	/**
	 * Decodes a specific number of bytes from the PCM data, and returns it as an array.
	 *
	 * @param bytesToRead Number of bytes to read
	 * @return Array containing the decoded bytes, with the specified length
	 */
	public byte[] readBytes(int bytesToRead) {
		byte[] bytes = new byte[bytesToRead];

		for (int i = 0; i < bytesToRead; i++)
			bytes[i] = (byte) BitByteConv.bitArrayToInt(readBits(Byte.SIZE), true);

		return bytes;
	}
}
