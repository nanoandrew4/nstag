package nsteg.processors;

import nsteg.Spinner;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class AudioProcessor {

	/**
	 * Loads the PCM data from an AudioInputStream into a byte array, and returns it. In order to be memory efficient,
	 * primitive arrays are used, and scaled by making a bigger array and copying the data over. This approach is also
	 * much faster than using alternative data structures. Once all the data has been read in, an array of the
	 * appropriate size is created to hold the PCM bytes, and the data is copied from the buffer to this array.
	 *
	 * @param audioFile AudioInputStream to be read and converted to a PCM byte array
	 * @return Byte array containing the PCM data of the audio file
	 */
	public static byte[] loadAudioFile(@NotNull AudioInputStream audioFile) {
		int bytesPerSample = audioFile.getFormat().getChannels() * 2;
		byte[] byteSample = new byte[bytesPerSample];

		byte[] buffer = new byte[(int) Math.pow(2, 20)];
		byte[] decBytes = null;
		int bytesRead = 0;

		try {
			while (audioFile.read(byteSample) != -1) {
				System.arraycopy(byteSample, 0, buffer, bytesRead, bytesPerSample);
				bytesRead += bytesPerSample;

				if (bytesRead + bytesPerSample > buffer.length) {
					byte[] tmp = buffer;
					buffer = new byte[tmp.length * 2];
					System.arraycopy(tmp, 0, buffer, 0, tmp.length);
				}
			}

			decBytes = new byte[bytesRead];
			System.arraycopy(buffer, 0, decBytes, 0, bytesRead);
		} catch (IOException e) {
			System.err.println("Error reading audio file into memory");
		}

		return decBytes;
	}

	/**
	 * Writes an array of PCM data to a WAV container.
	 *
	 * @param outName    Desired name for the wav file
	 * @param pcm        Array of PCM bytes that are to be written
	 * @param channels   Number of channels to use for encoding to WAV
	 * @param sampleRate Sample rate to be used for encoding to WAV
	 */
	public static void writePCMToWAV(@NotNull String outName, @NotNull byte[] pcm, int channels, int sampleRate) {
		AudioFormat f = new AudioFormat(sampleRate, 16, channels, true, false);
		try {
			Spinner.end();
			System.out.println();
			Spinner.printWithSpinner("Writing encoded audio file to disk... ");

			AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), f, pcm.length),
					AudioFileFormat.Type.WAVE, new File(outName)
			);

			Spinner.spin();
			System.out.println("Data encoded successfully into audio file \"" + outName + "\"");
		} catch (IOException e) {
			System.err.println("Error writing encoded PCM to WAV file");
		}
	}
}
