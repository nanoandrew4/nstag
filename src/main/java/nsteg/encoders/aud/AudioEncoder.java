package nsteg.encoders.aud;

import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Crypto;
import nsteg.processors.AudioProcessor;

import javax.sound.sampled.AudioInputStream;
import javax.validation.constraints.NotNull;

/**
 * This class handles the encoding of data into the PCM bytes of an audio file.
 */
public class AudioEncoder extends Encoder {
	private byte[] audBytes; // PCM data of the audio file that is to be used to encode data

	private int currLSB = 0, currByte = 0;
	private int LSBsToUse = 1;
	private int channels, sampleRate;

	/**
	 * Creates a new instance of AudioEncoder, loads the passed AudioInputStream into an array, and writes the number
	 * of least significant bits that will be used for encoding to the PCM byte array.
	 *
	 * @param audFile AudioInputStream of the audio file that will be used for encoding
	 * @param LSBsToUse Number of least significant bits to use in the right channel (left is untouched)
	 */
	public AudioEncoder(@NotNull AudioInputStream audFile, int LSBsToUse) {
		this.channels = audFile.getFormat().getChannels();
		this.sampleRate = (int) audFile.getFormat().getSampleRate();
		audBytes = AudioProcessor.loadAudioFile(audFile);

		encodeBits(BitByteConv.intToBitArray(LSBsToUse, LSB_BITS_COUNT));
		this.LSBsToUse = LSBsToUse;
	}

	/**
	 * Returns the PCM byte array that this AudioEncoder instance is working on.
	 * @return PCM byte array containing original PCM data, with any changes made by this class to the byte array
	 */
	public byte[] getEncodedPCM() {
		return audBytes;
	}

	/**
	 * Returns the number of channels that the audio file being worked with has.
	 * @return Number of channels in the audio file
	 */
	public int getChannels() {
		return channels;
	}

	/**
	 * Returns the sample rate of the audio file being worked on.
	 * @return Sample rate of the audio file
	 */
	public int getSampleRate() {
		return sampleRate;
	}

	/**
	 * Encodes the specified bits to the PCM byte array. This is done by disassembling the current byte being worked on,
	 * and inserting the bits into the least significant bit(s) of the byte. Once the bits have been inserted, the byte
	 * is reassembled and overwrites its old value in the array. Only bytes belonging to the right channel are written
	 * to, since modifying the left channel even slightly causes great distortions in the sound.
	 *
	 * @param bits Array of bits to encode into the audio file
	 */
	public void encodeBits(@NotNull byte[] bits) {
		int bitPos = 0;
		for (; currByte < audBytes.length && bitPos < bits.length; ) {
			byte[] byteBits = BitByteConv.intToBitArray(audBytes[currByte], Byte.SIZE);

			// Write bits to the least significant bit(s), until no more bits can be written to current byte, or all bits have been written
			for (; currLSB < LSBsToUse && bitPos < bits.length; currLSB++)
				byteBits[byteBits.length - 1 - currLSB] = bits[bitPos++];
			audBytes[currByte] = (byte) BitByteConv.bitArrayToInt(byteBits, true);

			/*
			 * If no more bits to write in this byte, move on two bytes. This is done to prevent audible changes, since
			 * most music has two channels, and apparently changing the data in the second results in barely any
			 * perceptible distortion.
			 */
			if (currLSB == LSBsToUse) {
				currLSB = 0;
				currByte += 2;
			}
		}

		if (currByte == audBytes.length) // Should never happen
			System.err.println("No more audio data to use...");
	}

	/**
	 * Encodes the specified bytes to the PCM byte array. This method uses encodeBits(byte[] bits) internally.
	 * @param bytesToEncode Array of bytes to be encoded
	 */
	public void encodeBytes(@NotNull byte[] bytesToEncode) {
		for (byte b : bytesToEncode)
			encodeBits(BitByteConv.intToBitArray(b, Byte.SIZE));
	}

	// See abstract method for docs
	public boolean doesFileFit(int fileSizeInBits, int LSBsToUse, boolean encrypted) {
		long requiredBits = LSB_BITS_COUNT + (2 * SIZE_BITS_COUNT) + fileSizeInBits;
		if (encrypted)
			requiredBits += Crypto.GCM_AAD_SIZE + Crypto.AES_IV_SIZE + Crypto.SALT_SIZE_BITS;

		int maxCapacity = ((audBytes.length * Byte.SIZE) / 2) * LSBsToUse;

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
		// Impl pending, probably in next minor release
	}
}
