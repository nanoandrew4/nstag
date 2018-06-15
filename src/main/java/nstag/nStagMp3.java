package nstag;

import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class nStagMp3 {
	public static void encode(String origPath, String fileToEncode, String outName) {
		byte[] bytes;
		AudioInputStream rawAudioStream, decodedAudioStream;
		AudioFormat streamFormat;
		try {
			rawAudioStream = AudioSystem.getAudioInputStream(new File(origPath));
			bytes = Files.readAllBytes(Paths.get(fileToEncode));
			streamFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rawAudioStream.getFormat().getSampleRate(), 16,
					rawAudioStream.getFormat().getChannels(), rawAudioStream.getFormat().getChannels() * 2,
					rawAudioStream.getFormat().getSampleRate(), false
			);
			decodedAudioStream = AudioSystem.getAudioInputStream(streamFormat, rawAudioStream);
		} catch (IOException e) {
			System.err.println("Input files could not be found, please input the correct path, name and extension");
			return;
		} catch (UnsupportedAudioFileException e) {
			e.printStackTrace();
			return;
		}

		int bytesPerSample = decodedAudioStream.getFormat().getChannels() * 2;
		ArrayList<Byte> bytesList = new ArrayList<>();
		ArrayDeque<Byte> bitsToInsert = new ArrayDeque<>();
		byte[] bytesToRead = new byte[bytesPerSample];

		for (byte aByte : bytes) {
			int[] bits = nStag.getBits(aByte, 8, true);
			for (int b : bits)
				bitsToInsert.add((byte) b);
		}

		try {
			while ((decodedAudioStream.read(bytesToRead)) != -1 && bitsToInsert.size() > 0)
				for (int i = 0; i < bytesPerSample && bitsToInsert.size() > 0; i++) {
					if (i % 2 == 0) {
						int[] bits = nStag.getBits(bytesToRead[i], 8, true);
						bits[7] = bitsToInsert.pop();
						int moddedByte = nStag.toByte(bits, true);
						bytesList.add((byte) moddedByte);
					} else
						bytesList.add(bytesToRead[i]);
				}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		byte[] decodedArr = new byte[bytesList.size()];
		for (int i = 0; i < decodedArr.length; i++)
			decodedArr[i] = bytesList.get(i);

		byte[] encoded = encodePCMToMP3(decodedArr, streamFormat);
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(new File(outName));
			fos.write(encoded);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static byte[] encodePCMToMP3(byte[] pcm, AudioFormat audioFormat) {
		LameEncoder encoder = new LameEncoder(audioFormat, 256, MPEGMode.STEREO, Lame.QUALITY_HIGHEST, false);

		ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
		byte[] buffer = new byte[encoder.getPCMBufferSize()];

		int bytesToTransfer = Math.min(buffer.length, pcm.length);
		int bytesWritten;
		int currentPcmPosition = 0;
		while (0 < (bytesWritten = encoder.encodeBuffer(pcm, currentPcmPosition, bytesToTransfer, buffer))) {
			currentPcmPosition += bytesToTransfer;
			bytesToTransfer = Math.min(buffer.length, pcm.length - currentPcmPosition);

			mp3.write(buffer, 0, bytesWritten);
		}

		encoder.close();
		return mp3.toByteArray();
	}

	public static void decode() {

	}
}
