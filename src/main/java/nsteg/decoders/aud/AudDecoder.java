package nsteg.decoders.aud;

import nsteg.decoders.Decoder;
import nsteg.encoders.aud.AudEndState;
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
 * When a small number of bits must be read, such as the file metadata, they are read on the application thread, since
 * using threading in this case would be overkill, but when large number of bits must be read, for example, when reading
 * the file, threading is used to speed up the decoding process.
 * <p><br>
 * Since data was only encoded in the right channel bytes, the decoder skips all left channel bits, meaning that
 * it moves in steps of two.
 */
public class AudDecoder extends Decoder {
	private AudDecoderThread[] decThreads = new AudDecoderThread[Runtime.getRuntime().availableProcessors()];

	// Holds the PCM data of the audio file
	private byte[] encodedBytes;

	// Used to keep track of where the decoding process is at in the PCM byte array
	private int currLSB = 0, currPCMByte = 0;

	// Number of least significant bits to read from each right channel byte of PCM data
	private int LSBsToUse = 1;

	/**
	 * Creates a new instance of AudDecoder, which loads the audio file to a byte array. The number of least
	 * significant bits used during encoding is also read. Once this constructor is done, the decoder is ready.
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
			AudioInputStream decodedStream = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, rawStream);
			this.encodedBytes = AudioProcessor.loadAudioFile(decodedStream);
		}

		initThreads();
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
		initThreads();
	}

	/**
	 * Reads the number of least significant bits used during encoding and initializes the threads that will be used for
	 * decoding large numbers of bytes, in the readBytes() method.
	 */
	private void initThreads() {
		LSBsToUse = BitByteConv.bitArrayToInt(readBits(LSB_BITS_COUNT), false);
		for (int t = 0; t < decThreads.length; t++) {
			decThreads[t] = new AudDecoderThread(encodedBytes);
			decThreads[t].setLSBsToUse(LSBsToUse);
			decThreads[t].start();
		}
	}

	@Override
	// Desc in Decoder class
	public void stopThreads() {
		for (AudDecoderThread t : decThreads) t.stopThread();
	}

	/**
	 * Decodes a specific number of bits from the PCM audio data, and returns them as an array. Only right channel bytes
	 * are read from, since the encoder does not touch left channel bytes.
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
	 * Decodes a specific number of bytes from the PCM audio data, and returns it as an array. This method employs the
	 * classes threads in order to speed up the decoding process, which is useful when handling large amounts of data.
	 * Each threads is assigned a number of bytes to decode, and the position they should write at in the byte array.
	 * Each thread then reads bits from the PCM audio data, assembles them into bytes and writes them to the byte array.
	 * The threads only read from right channel bytes, since the encoder does not touch left channel bytes.
	 *
	 * @param bytesToRead Number of bytes to read
	 * @return Array containing the decoded bytes, with the specified length
	 */
	public byte[] readBytes(int bytesToRead) {
		byte[] byteArr = new byte[bytesToRead];

		int currByteArrPos = 0;
		int remainingBytes = bytesToRead;
		int approxBytesPerThread = bytesToRead / decThreads.length + 1;
		for (AudDecoderThread decThread : decThreads) {
			// Number of bytes that the thread should write to the file byte array
			approxBytesPerThread = approxBytesPerThread < remainingBytes ? approxBytesPerThread : remainingBytes;

			// Information regarding where the thread will end the decoding job
			AudEndState endState = decThread.submitJob(byteArr, currPCMByte, currByteArrPos, currLSB, approxBytesPerThread);
			currByteArrPos += approxBytesPerThread;
			currPCMByte = endState.endByte;
			currLSB = endState.endLSB;

			remainingBytes -= approxBytesPerThread;
		}

		return byteArr;
	}
}
