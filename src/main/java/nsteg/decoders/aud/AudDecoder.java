package nsteg.decoders.aud;

import nsteg.decoders.Decoder;
import nsteg.encoders.aud.FLACData;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.processors.AudioProcessor;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;

/**
 * This class is responsible for decoding data that was previously encoded into an audio file using AudEncoder.
 * Since data was only encoded in the right channel bytes, the decoder skips all left channel bits, meaning that
 * it moves in steps of two. This decoder assumes that the audio file has two channels only, left and right, since most
 * audio files are stereo (meaning they only have two channels).
 * <p><br>
 * It reads bits from the least significant bit(s) of each right channel byte. The number of least significant bits
 * used to originally encode the data is decoded when initializing this class. For more information on how the data
 * is encoded, see the Encoder class.
 *
 * @see nsteg.encoders.Encoder
 */
public class AudDecoder extends Decoder {
	// Holds the PCM data of the audio file
	private byte[] encodedBytes;

	// Used to keep track of where the decoding process is at in the PCM byte array
	private int currLSB = 0, currPCMByte = 0;

	// Number of least significant bits to read from each right channel byte of PCM data
	private int LSBsToUse = 1;

	/**
	 * Creates a new instance of AudDecoder, which reads the PCM data from an audio file and stores it in a byte array.
	 * The number of least significant bits used during encoding is also read.
	 *
	 * @param audioFileName Name of the audio file to decode the PCM byte array from
	 */
	public AudDecoder(@NotNull String audioFileName) {
		if (audioFileName.endsWith("flac")) {
			FLACData data = AudioProcessor.loadFLACFile(audioFileName);
			this.encodedBytes = data.pcm;
		} else {
			AudioInputStream rawStream;
			try {
				rawStream = AudioSystem.getAudioInputStream(new File(audioFileName));
			} catch (UnsupportedAudioFileException | IOException e) {
				System.err.println("Error opening audio stream.");
				return;
			}
			AudioInputStream decodedStream = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED,
																			 rawStream);
			this.encodedBytes = AudioProcessor.loadAudioFile(decodedStream);
		}

		LSBsToUse = BitByteConv.bitArrayToInt(readBits(LSB_BITS_COUNT), false);
	}

	/**
	 * For sole use by the unit testing classes, since they create a stream of random noise instead of reading from a
	 * file. Although this works for any stream, ideally the other constructor should be used for convenience and
	 * flexibility anywhere else in the program.
	 *
	 * @param audioStream Audio file stream, in PCM_SIGNED encoding format
	 */
	public AudDecoder(@NotNull AudioInputStream audioStream) {
		this.encodedBytes = AudioProcessor.loadAudioFile(audioStream);
		LSBsToUse = BitByteConv.bitArrayToInt(readBits(LSB_BITS_COUNT), false);
	}

	/**
	 * Decodes a specific number of bits from the PCM audio data, and returns them as an array. Only right channel
	 * bytes are read from, since the encoder does not touch left channel bytes.
	 *
	 * @param bitsToRead Number of bits to read
	 * @return Array containing the decoded bits, with the specified length
	 */
	public byte[] readBits(int bitsToRead) {
		byte[] bits = new byte[bitsToRead];
		int bitPos = 0;

		for (; currPCMByte < encodedBytes.length && bitPos < bitsToRead; ) {
			byte[] byteBits = BitByteConv.intToBitArray(encodedBytes[currPCMByte], Byte.SIZE);

			// Read the least significant bits until enough have been read, or no more left to read in current byte
			for (; currLSB < LSBsToUse && bitPos < bitsToRead; currLSB++)
				bits[bitPos++] = byteBits[byteBits.length - 1 - currLSB];

			if (currLSB == LSBsToUse) {
				currLSB = 0;
				currPCMByte += 2; // Skip left channel byte, go to next right channel byte
			}
		}

		return bits;
	}

	/**
	 * Decodes a specific number of bytes from the PCM audio data, and returns it as an array. Uses
	 * readBits(int bitsToRead) internally, to read each byte as eight bits.
	 *
	 * @param bytesToRead Number of bytes to read
	 * @return Array containing the decoded bytes, with the specified length
	 */
	public byte[] readBytes(int bytesToRead) {
		byte[] byteArr = new byte[bytesToRead];

		for (int i = 0; i < bytesToRead; i++)
			byteArr[i] = (byte) BitByteConv.bitArrayToInt(readBits(Byte.SIZE), true);

		return byteArr;
	}

	@Override
	public void stopThreads() {
		// Not applicable
	}
}
