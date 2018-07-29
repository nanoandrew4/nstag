package nsteg.decoders;

import nsteg.Spinner;
import nsteg.decoders.aud.AudDecoder;
import nsteg.decoders.img.ImgDecoder;
import nsteg.encoders.Encoder;
import nsteg.nsteg_utils.BitByteConv;
import nsteg.nsteg_utils.Compressor;
import nsteg.nsteg_utils.Crypto;

import javax.imageio.ImageIO;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Abstract class declaring the methods any decoder implementation must have. Also handles initializing the right decoder
 * when asked to decode data by Main, and running the whole higher level part of the decoding process.
 */
public abstract class Decoder {

	/**
	 * Defines the number of bits that are used for encoding the size of the file to be decoded.
	 */
	private static final int SIZE_BITS_COUNT = Encoder.SIZE_BITS_COUNT;

	/**
	 * Defines the number of bits that were used for encoding the number of least significant bits that were used during
	 * encoding.
	 */
	protected final static int LSB_BITS_COUNT = Encoder.LSB_BITS_COUNT;

	// Impl specific
	public abstract byte[] readBits(int bitsToRead);

	// Impl specific
	public abstract byte[] readBytes(int bytesToRead);

	/**
	 * Stops all threads being used by this decoder instance. Calling this method is required to ensure that the data
	 * has been fully read, since it waits for all the threads to finish before returning.
	 */
	public abstract void stopThreads();

	/**
	 * Given a filename, returns the decoder that is able to decode data from the file by attempting to open it with
	 * all available Decoder implementations, and returning whichever handled the file successfully.
	 *
	 * @param encodedMediaFile File name of the media file to be used for the encoding process
	 * @return Decoder capable of decoding data from the file passed
	 */
	private static Decoder getDecoder(@NotNull String encodedMediaFile) {
		Spinner.printWithSpinner("Loading media file containing encoded data... ");

		Decoder decoder = null;

		String[] fileSplit = encodedMediaFile.split("\\.");
		String fileExt = fileSplit[fileSplit.length - 1];
		if (Encoder.outImgFormats.contains(fileExt)) {
			try {
				decoder = new ImgDecoder(ImageIO.read(new File(encodedMediaFile)));
			} catch (IOException ignored) {
			}
		} else if (Encoder.outAudFormats.contains(fileExt))
			decoder = new AudDecoder(encodedMediaFile); // Or MP3, but only relevant bit is it being FLAC

		Spinner.end();

		if (decoder == null) // Should never happen, Main checks inputs
			System.err.println("File format not supported.");
		return decoder;
	}

	/**
	 * Decodes a file from a media file that was encoded using this program. If file requires decryption, it also allows
	 * the user to decrypt the data provided the data has not been tampered with.
	 *
	 * @param encodedMedFile Name of the media file containing the data that is to be decoded
	 * @param outFileName    Name under which to save the encoded file (include file name extension)
	 */
	public static void decode(@NotNull String encodedMedFile, @NotNull String outFileName) {
		boolean encrypted = Crypto.offerToCrypt(false);

		Decoder decoder = getDecoder(encodedMedFile);
		if (decoder == null)
			return;

		Spinner.printWithSpinner("Extracting metadata from image... ");
		// Read compressed and uncompressed file sizes
		byte[] compFileSizeBits = decoder.readBits(SIZE_BITS_COUNT);
		byte[] uncompFileSizeBits = decoder.readBits(SIZE_BITS_COUNT);

		int compFileSize = BitByteConv.bitArrayToInt(compFileSizeBits, false);
		int uncompFileSize = BitByteConv.bitArrayToInt(uncompFileSizeBits, false);

		byte[] saltBytes = null;
		if (encrypted)
			saltBytes = decoder.readBytes(Crypto.SALT_SIZE_BITS / Byte.SIZE);

		Spinner.printWithSpinner("Extracting file data from image... ");
		// Read compressed bytes, decrypt if necessary, then decompress
		byte[] dataBytes = decoder.readBytes(compFileSize);
		decoder.stopThreads();

		Spinner.end();
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
