package nstag;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class nStagImg extends nStag {

	protected final static int BITS_PER_CHANNEL_BITS = 4; // Nibble

	/**
	 * Encodes a file into an image, using the least significant bit(s) in the ARGB channels of each pixel to store the
	 * data. Optionally encrypts the data in the file using the AES128 cipher.
	 *
	 * @param origPath       Path and name to image into which to encode the data
	 * @param fileToEncode   File to be encoded into the image
	 * @param outName        Path and name of the desired output image (copy of the original + file data)
	 * @param bitsPerChannel Number of least significant bits to use in each color channel. Using more bits will result
	 *                       in a greater visual deviation from the original. Range is 1-8
	 */
	public static void encode(String origPath, String fileToEncode, String outName, int bitsPerChannel) {
		boolean encrypt = false;
		System.out.print("Do you wish to encrypt the data? Y/N: ");
		if ("y".equalsIgnoreCase(in.nextLine()))
			encrypt = true;

		byte[] keyBits = null, dataBytes;

		try {
			Spinner.printWithSpinner("Loading file to encode... ");
			dataBytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
			System.err.println("File to be encoded could not be found, please input the correct path, name and extension");
			return;
		}

		BufferedImage original;
		try {
			Spinner.printWithSpinner("\nLoading image to encode to... ");
			original = ImageIO.read(new File(origPath));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		ImgEncoder ie = new ImgEncoder(original, bitsPerChannel);

		if (encrypt) {
			Spinner.printWithSpinner("Encrypting data... ");
			byte[][] keyAndDataBits = encrypt(dataBytes); // Contains key bits and encrypted data byte array, respectively
			keyBits = keyAndDataBits[0]; // Key bits, encoded after file size and bits per channel, for later decryption
			dataBytes = keyAndDataBits[1];
		}

		// TODO: BITS PER CHANNEL MUST TAKE FIRST PIXEL ON ITS OWN, OTHERWISE DECODING METADATA AND DATA IS IMPOSSIBLE
		int numOfMetadataBits = SIZE_BITS_COUNT + (encrypt ? KEY_BITS_COUNT : 0);
		int dataBitsToPadWith = 0;
		if (numOfMetadataBits % (bitsPerChannel * 4) != 0)
			dataBitsToPadWith = (bitsPerChannel * 4) - (numOfMetadataBits % (bitsPerChannel * 4));

		byte[] metadataBits = new byte[numOfMetadataBits + dataBitsToPadWith];

		// Bits representing the file size (encrypted or otherwise, depending on what the user chooses)
		byte[] fileSizeBitsArr = intToBitArray(dataBytes.length, SIZE_BITS_COUNT, false); // File size in bytes
		System.arraycopy(fileSizeBitsArr, 0, metadataBits, 0, fileSizeBitsArr.length);

		/*
		 * If number of bits to encode is larger than number of bits that can be encoded in image, notify user and
		 * return. The image can hold: (pixels * 4 * (bits per channel)). Each pixel has 4 channels, alpha, red, green
		 * and blue.
		 */
		long requiredBits = fileSizeBitsArr.length + dataBytes.length * 8 + (encrypt ? keyBits.length * 8 : 0);
		if (requiredBits > original.getWidth() * original.getHeight() * bitsPerChannel * 4) {
			System.err.println("Not enough space in image, consider allowing more bits");
			System.err.println("Required bits: " + requiredBits);
			System.err.println("Available bits: " + (original.getWidth() * original.getHeight() * bitsPerChannel * 4));
			return;
		}

		Spinner.printWithSpinner("Encoding data to image... ");

		/*
		 * Write metadata bits (file size, bits per channel, and key if encryption is used) and data bits to the least
		 * significant bits of each pixel, until all the data has been written. Then just copy the pixel values.
		 */

		if (encrypt)
			System.arraycopy(keyBits, 0, metadataBits, SIZE_BITS_COUNT + BITS_PER_CHANNEL_BITS, keyBits.length);

		int currNibble = 0;
		for (int i = 0; i < dataBitsToPadWith / BITS_PER_CHANNEL_BITS; i++) {
			byte[] bits = intToBitArray(dataBytes[currNibble / 2], 8, true);
			if (currNibble % 2 == 0)
				System.arraycopy(bits, 0, metadataBits, numOfMetadataBits + i * 4, 4);
			else
				System.arraycopy(bits, 4, metadataBits, numOfMetadataBits + i * 4, 4);
			currNibble++;
		}
//		ie.encodeBits(metadataBits);

//		ie.encodeBytes(dataBytes, currNibble);

		try {
			Spinner.printWithSpinner("Writing encoded image to disk... ");
			ImageIO.write(ie.getImg(), "png", new File(outName));
			Spinner.spin();
			System.out.println("\nData encoded successfully into image: \"" + outName + "\"\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Decodes a file from an image that was encoded using this program. If file requires decryption, it also allows the
	 * user to decrypt the data provided the data has not been tampered with.
	 *
	 * @param encodedPath Path and name of the image containing the encoded file
	 * @param outFileName Path and name under which to save the encoded file (include file name extension)
	 */
	public static void decode(String encodedPath, String outFileName) {
		boolean encrypted = false;
		System.out.print("Is this data encrypted? Y/N: ");
		if ("y".equalsIgnoreCase(in.nextLine()))
			encrypted = true;

		BufferedImage encoded;
		try {
			Spinner.printWithSpinner("Loading image to decode... ");
			encoded = ImageIO.read(new File(encodedPath));
		} catch (IOException e) {
			System.err.println("Input file could not be found, please input the correct path, name and file extension");
			return;
		}

		ImgDecoder id = new ImgDecoder(encoded);

		Spinner.printWithSpinner("Extracting data from image... ");

		int bitsInImage = bitArrayToInt(id.readBits(32), false);

		byte[] keyBytes = null;
		if (encrypted)
			keyBytes = id.readBytes(KEY_BITS_COUNT / 8);

		System.out.println();

		byte[] dataBytes = id.readBytes(bitsInImage);

		if (encrypted) {
			Spinner.printWithSpinner("Decrypting data... ");
			dataBytes = decrypt(dataBytes, keyBytes);
		}

		try {
			Spinner.printWithSpinner("Writing decoded data to disk... ");
			FileOutputStream fos = new FileOutputStream(outFileName);
			fos.write(dataBytes);
			fos.close();
			Spinner.spin();
			System.out.println("Data decoded successfully into file: \"" + outFileName + "\"\n\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
