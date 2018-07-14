package nsteg.decoders;

import nsteg.Spinner;
import nsteg.decoders.aud.AudioDecoder;
import nsteg.encoders.Encoder;
import nsteg.decoders.img.ImgDecoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Compressor;
import nsteg.nsteg_utils.Crypto;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

public abstract class Decoder {
	private static final int SIZE_BITS_COUNT = Encoder.SIZE_BITS_COUNT;

	protected final static int LEAST_SIG_BITS_TO_USE = Encoder.LEAST_SIG_BITS_TO_USE;

	public abstract byte[] readBits(int bitsToRead);

	public abstract byte[] readBytes(int bytesToRead);

	private static Decoder getDecoder(String encodedMediaFile) {
		BufferedImage img;
		AudioInputStream raw;

		try {
			img = ImageIO.read(new URL(encodedMediaFile));
			return new ImgDecoder(img);
		} catch (IOException ignored) {
		}

		try {
			raw = AudioSystem.getAudioInputStream(new File(encodedMediaFile));
			AudioInputStream decoded = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, raw);
			return new AudioDecoder(decoded);
		} catch (UnsupportedAudioFileException | IOException ignored) {
		}

		System.err.println("File format not supported.");
		return null;
	}

	/**
	 * Decodes a file from an image that was encoded using this program. If file requires decryption, it also allows the
	 * user to decrypt the data provided the data has not been tampered with.
	 *
	 * @param encodedPath Path and name of the image containing the encoded file
	 * @param outFileName Path and name under which to save the encoded file (include file name extension)
	 */
	public static void decode(String encodedPath, String outFileName) {
		boolean encrypted = Crypto.offerToCrypt(false);

		Decoder decoder = getDecoder(encodedPath);
		if (decoder == null)
			return;

		Spinner.printWithSpinner("Extracting metadata from image... ");
		byte[] compFileSizeBits = decoder.readBits(SIZE_BITS_COUNT);
		byte[] uncompFileSizeBits = decoder.readBits(SIZE_BITS_COUNT);

		int compFileSize = BitByteConv.bitArrayToInt(compFileSizeBits, false);
		int uncompFileSize = BitByteConv.bitArrayToInt(uncompFileSizeBits, false);

		byte[] saltBytes = null;
		if (encrypted)
			saltBytes = decoder.readBytes(Crypto.SALT_SIZE_BITS / Byte.SIZE);

		Spinner.printWithSpinner("Extracting file data from image... ");
		byte[] dataBytes = decoder.readBytes(compFileSize);

		Spinner.spin();
		if (encrypted)
			dataBytes = Crypto.decrypt(dataBytes, saltBytes, Crypto.genAAD(uncompFileSizeBits, compFileSizeBits));

		dataBytes = Compressor.decompress(dataBytes, uncompFileSize);

		try {
			Spinner.printWithSpinner("Writing decoded data to disk... ");
			FileOutputStream fos = new FileOutputStream(outFileName);
			fos.write(dataBytes);
			fos.close();

			Spinner.end();
			System.out.println("Data written successfully into file: \"" + outFileName + "\"");
			System.out.println("Done!\n");
		} catch (IOException e) {
			System.err.println("Could not write data to disk");
		}
	}
}
