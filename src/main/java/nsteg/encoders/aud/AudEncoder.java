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
 * perceivable.
 */
public class AudEncoder extends Encoder {
	private AudEncoderThread[] encThreads = new AudEncoderThread[Runtime.getRuntime().availableProcessors()];

	private byte[] audBytes; // PCM data of the audio file that is to be used to encode data

	private int currLSB = 0, currByte = 0;
	private int channels, sampleRate, bitsPerSample; // Stored for writing to disk later

	/**
	 * Creates a new instance of AudEncoder, which loads the requested file into a PCM byte array. Some metadata is also
	 * read from the audio file for later encoding, to ensure the sound quality is as consistent as can be.
	 * Once this constructor is done, the encoder is ready to encode any data.
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

			initThreads(audBytes, LSBsToUse);
		} else {
			AudioInputStream rawStream;
			try {
				rawStream = AudioSystem.getAudioInputStream(new File(audioFileName));
			} catch (UnsupportedAudioFileException | IOException e) {
				System.err.println("Error opening the audio stream.");
				return;
			}

			AudioInputStream decodedStream = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, rawStream);
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
	 * Initializes some metadata variables for later use when re-encoding the PCM byte data to the user requested format,
	 * as well as reading the PCM byte data from the audio stream.
	 */
	private void initWithStream(AudioInputStream audioStream, int LSBsToUse) {
		this.channels = audioStream.getFormat().getChannels();
		this.bitsPerSample = audioStream.getFormat().getSampleSizeInBits();
		this.sampleRate = (int) audioStream.getFormat().getSampleRate();

		this.audBytes = AudioProcessor.loadAudioFile(audioStream);

		initThreads(audBytes, LSBsToUse);
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
	 * Encodes the specified bits to the PCM byte array. This is done by disassembling the current byte being worked on,
	 * and inserting the bits into the least significant bit(s) of the byte. Once the bits have been inserted, the byte
	 * is reassembled and overwrites its old value in the array. Only bytes belonging to the right channel are written
	 * to, since modifying the left channel even slightly causes great distortions in the sound.
	 *
	 * @param bits Array of bits to encode into the audio file
	 */
	public void encodeBits(@NotNull byte[] bits) {
		for (int i = 0; i < encThreads.length; i %= encThreads.length) {
			if (!encThreads[i].isActive()) {
				AudEndState endState = encThreads[i].submitJob(currByte, currLSB, bits);
				currByte = endState.endByte;
				currLSB = endState.endLSB;
				return;
			} else
				sleep(1);
			i++;
		}
	}

	/**
	 * Encodes the specified bytes to the PCM byte array. This method uses encodeBits(byte[] bits) internally.
	 * The bytes are split amongst the threads. This method converts chunks of bytes into an array of bits, and passes
	 * them on to the threads, to split the workload.
	 *
	 * @param bytesToEncode Array of bytes to be encoded
	 */
	public void encodeBytes(@NotNull byte[] bytesToEncode) {
		int currByte = 0;
		int bytesSize = bytesToEncode.length;
		for (; currByte < bytesToEncode.length; ) {
			int bytesPerThread = bytesToEncode.length / encThreads.length;
			bytesPerThread = (bytesSize < bytesPerThread ? bytesSize : bytesPerThread);
			byte[] bits = new byte[bytesPerThread * Byte.SIZE];
			for (int i = 0; i < bytesPerThread; i++)
				System.arraycopy(BitByteConv.intToBitArray(bytesToEncode[currByte++], Byte.SIZE), 0, bits, i * Byte.SIZE, Byte.SIZE);

			encodeBits(bits);

			bytesSize -= bytesPerThread;
		}
	}

	// See abstract method for docs
	public boolean doesFileFit(int fileSizeInBits, int LSBsToUse, boolean encrypted) {
		long requiredBits = LSB_BITS_COUNT + (2 * SIZE_BITS_COUNT) + fileSizeInBits;
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

	/**
	 * Initializes the threads and encodes the least significant bits to be used during the encoding process.
	 * Once this method returns, the threads are ready to encode.
	 *
	 * @param pcm       PCM byte array representing the audio file into which the data will be encoded
	 * @param LSBsToUse Number of least significant bits to use on each right channel byte. More allows for more data to
	 *                  be stored, but will cause greater distortion. 1-4 recommended.
	 */
	private void initThreads(byte[] pcm, int LSBsToUse) {
		for (int i = 0; i < encThreads.length; i++) {
			encThreads[i] = new AudEncoderThread(pcm);
			encThreads[i].start();
		}

		encodeBits(BitByteConv.intToBitArray(LSBsToUse, LSB_BITS_COUNT));
		while (encThreads[0].isActive())
			sleep(1);
		for (AudEncoderThread t : encThreads)
			t.setLSBsToUse(LSBsToUse);
	}

	/**
	 * Stops all AudEncoderThread instances. Must be called once this ImgEncoder instance has finished writing data,
	 * otherwise the encoding will not complete successfully.
	 */
	@Override
	public void stopThreads() {
		for (AudEncoderThread encThread : encThreads) encThread.stopThread();
	}
}
