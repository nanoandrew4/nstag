package nsteg.processors;

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

		long start = System.currentTimeMillis();
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

//		System.out.println((System.currentTimeMillis() - start) / 1000.0);

		return decBytes;
	}

	private static void writePCMToWAV(String outName, byte[] pcm, int channels, int sampleRate) {
		AudioFormat f = new AudioFormat(sampleRate, 16, channels, true, false);
		try {
			AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), f, pcm.length),
					AudioFileFormat.Type.WAVE, new File(outName)
			);
		} catch (IOException e) {
			System.err.println("Error writing encoded PCM to WAV file");
		}
	}
}
