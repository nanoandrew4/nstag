package nsteg.processors;

import net.sourceforge.javaflacencoder.*;
import nsteg.nsteg_utils.Spinner;
import nsteg.encoders.aud.AudEncoder;
import nsteg.encoders.aud.FLACData;
import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.PCMProcessor;
import org.kc7bfi.jflac.metadata.StreamInfo;
import org.kc7bfi.jflac.util.ByteData;
import org.kc7bfi.jflac.util.WavWriter;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.validation.constraints.NotNull;
import java.io.*;

public class AudioProcessor {

	/**
	 * Returns a copy of the array that is passed as the argument, but that is twice as big as the passed array.
	 *
	 * @param arr Array to resize
	 * @param pos Position to check if out of bounds, to determine if resizing is necessary
	 * @return Resized array if pos was out of bounds, passed array otherwise
	 */
	private static byte[] resize(@NotNull byte[] arr, int pos) {
		if (pos > arr.length) {
			byte[] tmp = arr;
			arr = new byte[tmp.length * 2];
			System.arraycopy(tmp, 0, arr, 0, tmp.length);
		}

		return arr;
	}

	/**
	 * Returns a copy of the array that is passed as the argument, but that is sized according to the requested size
	 * by the user. This is useful if you have been using the resize(byte[] arr, int pos) method, and have an array
	 * that is too big for the data it contains. It will cut any excess data off, and return only an array containing
	 * relevant data.
	 *
	 * @param arr         Array to be trimmed
	 * @param desiredSize Desired length for the trimmed array
	 * @return Trimmed array
	 */
	private static byte[] trimArray(@NotNull byte[] arr, int desiredSize) {
		byte[] rArr = new byte[desiredSize];
		System.arraycopy(arr, 0, rArr, 0, desiredSize);
		return rArr;
	}

	/**
	 * Loads the PCM data from an AudioInputStream into a byte array, and returns it. In order to be memory efficient,
	 * primitive arrays are used, and scaled by making a bigger array and copying the data over. This approach is also
	 * much faster than using alternative data structures. Once all the data has been read in, an array of the
	 * appropriate size is created to hold the PCM bytes, and the data is copied from the buffer to this array.
	 * <p>
	 * Works for MP3 and WAV files.
	 *
	 * @param audioFile AudioInputStream to be read and converted to a PCM byte array
	 * @return Byte array containing the PCM data of the audio file
	 */
	public static byte[] loadAudioFile(@NotNull AudioInputStream audioFile) {
		int bytesPerSample = audioFile.getFormat().getChannels() * 2;
		byte[] byteSample = new byte[bytesPerSample];

		byte[] buffer = new byte[(int) Math.pow(2, 20)];
		int bytesRead = 0;

		try {
			while (audioFile.read(byteSample) != -1) {
				System.arraycopy(byteSample, 0, buffer, bytesRead, bytesPerSample);
				bytesRead += bytesPerSample;

				buffer = resize(buffer, bytesRead + bytesPerSample);
			}
		} catch (IOException e) {
			System.err.println("Error reading audio file into memory");
		}

		return trimArray(buffer, bytesRead);
	}

	/**
	 * Loads the PCM data from a FLAC audio file into FLACData object, which is later returned. Some of the relevant
	 * encoding metadata is returned as well, alongside the decoded PCM bytes.
	 *
	 * @param audioFileName Name of the FLAC audio file to decode (including audio file extension)
	 * @return FLACData object containing the data from the FLAC audio file
	 */
	public static FLACData loadFLACFile(@NotNull String audioFileName) {
		FLACData data = new FLACData();

		FLACDecoder decoder;
		try {
			decoder = new FLACDecoder(new FileInputStream(audioFileName));
		} catch (FileNotFoundException e) {
			System.err.println("Error opening FLAC audio file.");
			return data;
		}

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		WavWriter writer = new WavWriter(buffer);

		// More or less as per Decoder sample in library
		decoder.addPCMProcessor(new PCMProcessor() {
			@Override
			public void processStreamInfo(StreamInfo streamInfo) {
				data.bitsPerSample = streamInfo.getBitsPerSample();
				data.channels = streamInfo.getChannels();
				data.sampleRate = streamInfo.getSampleRate();
			}

			@Override
			public void processPCM(ByteData byteData) {
				try {
					writer.writePCM(byteData);
				} catch (IOException e) {
					System.err.println("Error while writing the PCM data from the FLAC audio file to the buffer.");
				}
			}
		});

		try {
			decoder.decode();
		} catch (IOException e) {
			System.err.println("Error decoding FLAC audio file.");
		}

		data.pcm = buffer.toByteArray();

		return data;
	}

	/**
	 * Writes an audio file containing the encoded data to disk, to the format requested by the user.
	 *
	 * @param outName    File name for the audio file containing the encoded data (including audio file extension)
	 * @param audEncoder AudEncoder instance with which the file was encoded to the audio file
	 */
	public static void writePCMToDisk(@NotNull String outName, @NotNull AudEncoder audEncoder) {
		String[] fileNameSplit = outName.split("\\.");
		String fileExt = fileNameSplit[fileNameSplit.length - 1];

		Spinner.end();
		System.out.println();
		Spinner.printWithSpinner("Writing encoded audio file to disk... ");

		if ("wav".equalsIgnoreCase(fileExt))
			writePCMToWAV(outName, audEncoder.getEncodedPCM(), audEncoder.getChannels(), audEncoder.getSampleRate());
		else if ("flac".equalsIgnoreCase(fileExt))
			writePCMToFLAC(outName, audEncoder.getEncodedPCM(), audEncoder.getBitsPerSample(),
						   audEncoder.getChannels(), audEncoder.getSampleRate());
	}

	/**
	 * Writes an array of PCM data to a WAV container.
	 *
	 * @param outName    Desired name for the wav file (including audio file extension)
	 * @param pcm        Array of PCM bytes that are to be written
	 * @param channels   Number of channels to use for encoding to WAV
	 * @param sampleRate Sample rate to be used for encoding to WAV
	 */
	private static void writePCMToWAV(@NotNull String outName, @NotNull byte[] pcm, int channels, int sampleRate) {
		AudioFormat f = new AudioFormat(sampleRate, 16, channels, true, false);
		try {
			AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), f, pcm.length),
							  AudioFileFormat.Type.WAVE, new File(outName));

			Spinner.end();
			System.out.println("Data encoded successfully into audio file \"" + outName + "\"");
		} catch (IOException e) {
			System.err.println("Error writing encoded PCM to WAV file");
		}
	}

	/**
	 * Writes an array of PCM data to a file using the FLAC codec, which provides lossless compression.
	 *
	 * @param outName    Desired name for the flac file (including audio file extension)
	 * @param pcm        Array of PCM bytes that are to be written
	 * @param channels   Number of channels to use for encoding the FLAC file
	 * @param sampleRate Sample rate to be used for encoding to FLAC
	 */
	private static void writePCMToFLAC(@NotNull String outName, @NotNull byte[] pcm, int bitsPerSample, int channels,
									   int sampleRate) {
		AudioFormat f = new AudioFormat(sampleRate, bitsPerSample, channels, true, false);
		AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), f, pcm.length);

		try {
			FLACOutputStream fos = new FLACFileOutputStream(outName);

			// Prepare the configuration so the encoder knows how to encode
			StreamConfiguration sConf = new StreamConfiguration();
			sConf.setChannelCount(channels);
			sConf.setSampleRate(sampleRate);
			sConf.setBitsPerSample(bitsPerSample);

			FLACEncoder encoder = new FLACEncoder();
			encoder.setStreamConfiguration(sConf);
			encoder.setThreadCount(Runtime.getRuntime().availableProcessors());

			encoder.setOutputStream(fos);
			encoder.openFLACStream();

			// Encode PCM data
			AudioStreamEncoder.encodeAudioInputStream(ais, 16384 /* Defined in AudioStreamEncoder*/, encoder, true);
			((FLACFileOutputStream) fos).close();

			Spinner.end();
			System.out.println("Data encoded successfully into audio file \"" + outName + "\"");
		} catch (IOException e) {
			System.err.println("Writing PCM data using FLAC codec failed.");
		}
	}
}
