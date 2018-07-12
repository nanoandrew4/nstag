package nsteg.aud;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class Audio {
	Audio() {
		try {
			AudioInputStream raw = AudioSystem.getAudioInputStream(new File("test.mp3"));
			AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, raw.getFormat().getSampleRate(), 16,
					raw.getFormat().getChannels(), raw.getFormat().getChannels() * 2,
					raw.getFormat().getSampleRate(), false
			);
			AudioInputStream decoded = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, raw);

			byte[] decBytes = readMP3ToPCM(decoded);
			encodePCMToWAV(decBytes, raw.getFormat().getChannels(), (int) raw.getFormat().getSampleRate());

		} catch (UnsupportedAudioFileException | IOException e) {
			e.printStackTrace();
		}
	}

	public static void encode() {
		try {
			AudioInputStream raw = AudioSystem.getAudioInputStream(new File("test.mp3"));
			AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, raw.getFormat().getSampleRate(), 16,
					raw.getFormat().getChannels(), raw.getFormat().getChannels() * 2,
					raw.getFormat().getSampleRate(), false
			);
			AudioInputStream decoded = AudioSystem.getAudioInputStream(af, raw);

			int audSize = (int) decoded.getFrameLength() * 1152; // May not be 1152


		} catch (UnsupportedAudioFileException | IOException e) {
			e.printStackTrace();
		}
	}

	private static void encodePCMToWAV(byte[] pcm, int channels, int sampleRate) {
		AudioFormat f = new AudioFormat(sampleRate, 16, channels, true, false);
		try {
			AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), f, pcm.length), AudioFileFormat.Type.WAVE, new File("out.wav"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static byte[] readMP3ToPCM(AudioInputStream decoded) {
		int bytesPerSample = decoded.getFormat().getChannels() * 2;
		byte[] byteSample = new byte[bytesPerSample];

		byte[] decBytes = new byte[(int) Math.pow(2, 20)];

		int bytesRead = 0;
		try {
			while (decoded.read(byteSample) != -1) {
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
}
