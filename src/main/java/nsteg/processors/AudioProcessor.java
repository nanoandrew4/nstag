package nsteg.processors;

import nsteg.Spinner;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class AudioProcessor {
	public static byte[] loadAudioFile(AudioInputStream audioFile) {
		int bytesPerSample = audioFile.getFormat().getChannels() * 2;
		byte[] byteSample = new byte[bytesPerSample];

		byte[] decBytes = new byte[(int) Math.pow(2, 20)];
		int bytesRead = 0;

		try {
			while (audioFile.read(byteSample) != -1) {
				System.arraycopy(byteSample, 0, decBytes, bytesRead, bytesPerSample);
				bytesRead += bytesPerSample;

				if (bytesRead + bytesPerSample > decBytes.length) {
					byte[] tmp = decBytes;
					decBytes = new byte[tmp.length * 2];
					System.arraycopy(tmp, 0, decBytes, 0, tmp.length);
				}
			}

			byte[] tmp = decBytes;
			decBytes = new byte[bytesRead];
			System.arraycopy(tmp, 0, decBytes, 0, bytesRead);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return decBytes;
	}

	public static void writePCMToWAV(String outName, byte[] pcm, int channels, int sampleRate) {
		AudioFormat f = new AudioFormat(sampleRate, 16, channels, true, false);
		try {
			Spinner.printWithSpinner("Writing encoded audio file to disk... ");
			AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), f, pcm.length),
					AudioFileFormat.Type.WAVE, new File(outName)
			);
			Spinner.spin();
			System.out.println("\nData encoded successfully into audio file \"" + outName + "\"");
		} catch (IOException e) {
			System.err.println("Error writing encoded PCM to WAV file");
		}
	}
}
