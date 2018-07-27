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
 * This class is responsible for decoding data that was previously encoded using the AudioEncoder from the PCM data
 * of an audio file.
 */
public class AudioDecoder extends Decoder {
	private AudDecoderThread[] decThreads = new AudDecoderThread[Runtime.getRuntime().availableProcessors()];

	private byte[] encodedBytes; // Holds the PCM data of the audio file

	private int currLSB = 0, currDecByte = 0;

	// Number of least significant bits to read from each byte of PCM data
	private int LSBsToUse = 1;

	/**
	 * Creates a new instance of AudioDecoder, which loads the audio file to a PCM byte array. The number of least
	 * significant bits used during encoding is also read. Once this constructor is done, the decoder is ready to decode.
	 *
	 * @param audioFileName Name of the audio file to decode the PCM byte array from
	 */
	public AudioDecoder(@NotNull String audioFileName) {
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
	 * @param audioStream Audio file stream, in PCM_SIGNED encoding
	 */
	public AudioDecoder(@NotNull AudioInputStream audioStream) {
		this.encodedBytes = AudioProcessor.loadAudioFile(audioStream);
		initThreads();
	}

	private void initThreads() {
		for (int t = 0; t < decThreads.length; t++) {
			decThreads[t] = new AudDecoderThread(encodedBytes);
			decThreads[t].start();
		}

		LSBsToUse = BitByteConv.bitArrayToInt(readBits(LSB_BITS_COUNT), false);
		AudDecoderThread.setLSBsToUse(LSBsToUse);
	}

	public void stopThreads() {
		for (int t = 0; t < decThreads.length;) {
			if (!decThreads[t].isActive()) {
				decThreads[t].stopRunning();
				try {
					decThreads[t].join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				t++;
			} else
				sleep(10);
		}
	}

	static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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
		for (; currDecByte < encodedBytes.length && bitPos < bitsToRead; ) {
			byte[] byteBits = BitByteConv.intToBitArray(encodedBytes[currDecByte], Byte.SIZE);

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
				currDecByte += 2;
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
		byte[] fileBytes = new byte[bytesToRead];

		int currFileByte = 0;
		int remainingBytes = bytesToRead;
		int approxBytesPerThread = bytesToRead / decThreads.length + 1;
		for (AudDecoderThread decThread : decThreads) {
			// Number of bytes that the thread should write to the file byte array
			approxBytesPerThread = approxBytesPerThread < remainingBytes ? approxBytesPerThread : remainingBytes;
			
			// Information regarding where the thread will end the decoding job
			AudEndState endState = decThread.submitJob(fileBytes, currDecByte, currFileByte, currLSB, approxBytesPerThread);
			currFileByte += approxBytesPerThread;
			currDecByte = endState.endByte;
			currLSB = endState.endLSB;

			remainingBytes -= approxBytesPerThread;
		}

		stopThreads();

		return fileBytes;
	}
}
