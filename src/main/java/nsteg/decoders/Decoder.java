package nsteg.decoders;

import nsteg.nsteg_utils.Spinner;
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
 * Abstract class declaring the methods any decoder implementation must have. Also handles initializing the right
 * decoder
 * when asked to decode data by Main or CLIParser, and running the whole higher level part of the decoding process.
 */
public abstract class Decoder {

	/**
	 * Defines the number of bits that are used for encoding the size of the file to be decoded.
	 */
	private static final int SIZE_BITS_COUNT = Encoder.SIZE_BITS_COUNT;

	/**
	 * Defines the number of bits that were used for encoding the number of least significant bits that were used
	 * during
	 * encoding.
	 */
	protected final static int LSB_BITS_COUNT = Encoder.LSB_BITS_COUNT;

	protected static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

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
	 * Given a filename, returns the decoder that can handle the given file extension, by checking with the supported
	 * codecs lists in the Encoder class.
	 *
	 * @param encodedMediaFile File name of the media file to be used for the encoding process
	 * @return Decoder instance capable of decoding data from the file passed, ready for use
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

		if (decoder == null) // Should never happen, Main and CLIParser check inputs for validity
			System.err.println("File format not supported.");
		return decoder;
	}

	/**
	 * Decodes file(s) from a media file that was encoded using this program. If the file(s) requires decryption, it
	 * also allows the user to decrypt them, provided the data has not been tampered with. For more info on how the
	 * data was encoded, see the Encoder class.
	 *
	 * @param encodedMedFile Name of the media file containing the data that is to be decoded
	 * @param decrypt        True to decrypt the file(s), false otherwise. May be null, in which case the user will
	 *                       be asked if they want to decrypt the file(s) or not. This argument exists to allow the
	 *                       program to be non-interactive, which is employed by CLIParser
	 * @param pass           Password to use for encryption. This field may be null, in which case the Crypto.decrypt()
	 *                       method will prompt the user for a password. Nulling this field is the safer approach,
	 *                       since  it means the password remains in memory for a much shorter period of time. This
	 *                       argument exists to allow the CLIParser to pass a password in, if the user chooses
	 * @see Encoder
	 */
	public static void decode(@NotNull String encodedMedFile, Boolean decrypt, String pass) {
		if (decrypt == null)
			decrypt = Crypto.offerToCrypt(false);

		Decoder decoder = getDecoder(encodedMedFile);
		if (decoder == null)
			return;

		Spinner.printWithSpinner("Extracting metadata from image... ");
		int numOfFiles = BitByteConv.bitArrayToInt(decoder.readBits(SIZE_BITS_COUNT), true);

		String[] fileNames = new String[numOfFiles];
		for (int s = 0; s < numOfFiles; s++) {
			int fileNameLen = BitByteConv.bitArrayToInt(decoder.readBits(SIZE_BITS_COUNT), true);
			byte[] fileNameBytes = decoder.readBytes(fileNameLen);
			fileNames[s] = new String(fileNameBytes);
		}

		int[] fileSizes = new int[numOfFiles];
		int uncompFilesSize = 0;
		for (int i = 0; i < numOfFiles; i++)
			uncompFilesSize += fileSizes[i] = BitByteConv.bitArrayToInt(decoder.readBits(SIZE_BITS_COUNT), true);

		// Read compressed size of the file(s) contained in the media file
		byte[] compFilesSizeBits = decoder.readBits(SIZE_BITS_COUNT);
		byte[] uncompFilesSizeBits = BitByteConv.intToBitArray(uncompFilesSize, Integer.SIZE);

		int compFilesSize = BitByteConv.bitArrayToInt(compFilesSizeBits, false);

		byte[] saltBytes = null;
		if (decrypt)
			saltBytes = decoder.readBytes(Crypto.SALT_SIZE_BITS / Byte.SIZE);

		Spinner.printWithSpinner("Extracting file data from image... ");
		byte[] filesBytes = decoder.readBytes(compFilesSize);
		decoder.stopThreads();

		Spinner.end();
		if (decrypt)
			filesBytes = Crypto.decrypt(filesBytes, saltBytes, Crypto.genAAD(uncompFilesSizeBits, compFilesSizeBits),
										pass);

		filesBytes = Compressor.decompress(filesBytes, uncompFilesSize);

		try {
			Spinner.printWithSpinner("Writing decoded data to disk... ");
			// Separate files from the continuous byte array, and write them to disk
			int byteCount = 0;
			for (int i = 0; i < numOfFiles; i++) {
				FileOutputStream fos = new FileOutputStream(fileNames[i]);

				byte[] fileBytes = new byte[fileSizes[i]];
				System.arraycopy(filesBytes, byteCount, fileBytes, 0, fileBytes.length);
				byteCount += fileBytes.length;

				fos.write(fileBytes);
				fos.close();
			}

			Spinner.end();
			System.out.print("\nData written successfully into file(s): ");
			for (int i = 0; i < numOfFiles; i++)
				System.out.print("\"" + fileNames[i] + "\" ");
			System.out.println("\nDone!\n");
		} catch (IOException e) {
			System.err.println("Could not write data to disk.");
		}
	}
}
