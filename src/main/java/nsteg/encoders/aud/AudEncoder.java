package nsteg.encoders.aud;

import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Crypto;
import nsteg.processors.AudioProcessor;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;

/**
 * This class handles the encoding of data into the PCM bytes of an audio file. The encoding process works as follows.
 * The PCM data from the audio file is decoded and stored in a byte array. The PCM byte array is then sent to the
 * worker threads, so that it can be worked on in parallel. From there encodeBits() and encodeBytes() take care of the
 * encoding of any relevant data, although the number of least significant bits being used is encoded in the
 * constructor of this class.
 * <p><br>
 * One thing to note is that only the right channel bytes are used, so half the bytes in the PCM byte
 * array are left untouched. This is because modifying the left channel byte even a bit causes very noticeable
 * distortions to the sound, whereas the right channel is more generous with how much can be modified without it being
 * perceivable. This program assumes stereo audio files are being used, since they are the most popular.
 */
public class AudEncoder extends Encoder {
	private byte[] audBytes; // PCM data of the audio file that is to be used to encode data

	// Encoding position trackers, so that data can be written continuously
	private int currLSB = 0, currByte = 0;
	private int LSBsToUse = 1;

	// Stored for re-encoding the audio file from the PCM bytes once the data encoding is done
	private int channels, sampleRate, bitsPerSample;

	/**
	 * Creates a new instance of AudEncoder, which loads the requested file into a PCM byte array. Some metadata is
	 * also read from the audio file for later encoding, to ensure the sound quality is as consistent as can be.
	 *
	 * @param audioFileName Name of the audio file that is to be used for encoding
	 * @param LSBsToUse     Number of least significant bits to use in the right channel (left is untouched)
	 */
	public AudEncoder(@NotNull String audioFileName, int LSBsToUse) {
		if (audioFileName.endsWith("flac")) {
			FLACData data = AudioProcessor.loadFLACFile(audioFileName);
			this.channels = data.channels;
			this.bitsPerSample = data.bitsPerSample;
			this.sampleRate = data.sampleRate;
			this.audBytes = data.pcm;

			encodeBits(BitByteConv.intToBitArray(LSBsToUse, LSB_BITS_COUNT));
			this.LSBsToUse = LSBsToUse;
		} else {
			AudioInputStream rawStream;
			try {
				rawStream = AudioSystem.getAudioInputStream(new File(audioFileName));
			} catch (UnsupportedAudioFileException | IOException e) {
				System.err.println("Error opening the audio stream.");
				return;
			}

			AudioInputStream decodedStream = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED,
																			 rawStream);
			initWithStream(decodedStream, LSBsToUse);
		}
	}

	/**
	 * For sole use by the unit testing classes, since they use a stream of random noise instead of reading data
	 * from a file. Although this works for any audio stream, ideally the other constructor should be used for
	 * convenience and flexibility anywhere else in the program.
	 *
	 * @param audioStream Audio file stream, in PCM_SIGNED encoding
	 * @param LSBsToUse   Number of least significant bits to encode with
	 */
	public AudEncoder(@NotNull AudioInputStream audioStream, int LSBsToUse) {
		initWithStream(audioStream, LSBsToUse);
	}

	/*
	 * Initializes some metadata variables for later use when re-encoding the PCM byte data to the user requested
	 * format, as well as reading the PCM byte data from the audio stream.
	 */
	private void initWithStream(AudioInputStream audioStream, int LSBsToUse) {
		this.channels = audioStream.getFormat().getChannels();
		this.bitsPerSample = audioStream.getFormat().getSampleSizeInBits();
		this.sampleRate = (int) audioStream.getFormat().getSampleRate();

		this.audBytes = AudioProcessor.loadAudioFile(audioStream);

		encodeBits(BitByteConv.intToBitArray(LSBsToUse, LSB_BITS_COUNT));
		this.LSBsToUse = LSBsToUse;
	}

	/**
	 * Returns the PCM byte array that this AudEncoder instance is working on.
	 *
	 * @return PCM byte array containing original PCM data, with any changes made by this class to the byte array
	 */
	public byte[] getEncodedPCM() {
		return audBytes;
	}

	/**
	 * Returns the number of channels that the audio file being worked with has.
	 *
	 * @return Number of channels in the audio file
	 */
	public int getChannels() {
		return channels;
	}

	/**
	 * Returns the sample rate of the audio file being worked on.
	 *
	 * @return Sample rate of the audio file
	 */
	public int getSampleRate() {
		return sampleRate;
	}

	/**
	 * Return the number of bits that each sample is made of.
	 *
	 * @return Bits per sample for the audio file being worked on
	 */
	public int getBitsPerSample() {
		return bitsPerSample;
	}

	/**
	 * Encodes the specified bits to the PCM byte array, by writing to the least significant bits of the right channel
	 * (the program assumes a stereo audio file), which barely cause any distortion. All left channel bytes are
	 * skipped, since even a minimal modification to that channel causes very audible distortions.
	 *
	 * @param bits Array of bits to encode into the audio file
	 */
	public void encodeBits(@NotNull byte[] bits) {
		int currBitPos = 0;
		while (currBitPos < bits.length) {
			byte[] byteBits = BitByteConv.intToBitArray(audBytes[currByte], Byte.SIZE);

			// Write bits to the least significant bit(s), until no more bits can be written to current byte, or all
			// bits have been written
			for (; currLSB < LSBsToUse && currBitPos < bits.length; currLSB++)
				byteBits[byteBits.length - 1 - currLSB] = bits[currBitPos++];
			audBytes[currByte] = (byte) BitByteConv.bitArrayToInt(byteBits, true);

			if (currLSB == LSBsToUse) {
				currLSB = 0;
				currByte += 2; // Skip left channel, go directly to the next right channel PCM byte
			}
		}
	}

	/**
	 * Encodes the specified bytes to the PCM byte array. This method uses encodeBits(byte[] bits) internally.
	 *
	 * @param bytesToEncode Array of bytes to be encoded
	 */
	public void encodeBytes(@NotNull byte[] bytesToEncode) {
		for (byte b : bytesToEncode)
			encodeBits(BitByteConv.intToBitArray(b, Byte.SIZE));
	}

	// See abstract method for docs
	public boolean doesFileFit(int fileSizeInBits, int numOfFiles, int LSBsToUse, boolean encrypted) {
		long requiredBits = LSB_BITS_COUNT + (2 * SIZE_BITS_COUNT) + fileSizeInBits + SIZE_BITS_COUNT +
							(SIZE_BITS_COUNT * numOfFiles);
		if (encrypted)
			requiredBits += Crypto.GCM_AAD_SIZE + Crypto.AES_IV_SIZE + Crypto.SALT_SIZE_BITS;

		long maxCapacity = (((long) audBytes.length * Byte.SIZE) / 2) * LSBsToUse;

		if (requiredBits > maxCapacity) {
			System.err.println("Audio file not long enough, consider allowing more bits or using another audio file");
			System.err.println("Required capacity: " + requiredBits);
			System.err.println("Bits that can be encoded: " + maxCapacity);
			System.out.println();
			return false;
		}

		return true;
	}

	@Override
	public void stopThreads() {
		// Not applicable
	}
}
