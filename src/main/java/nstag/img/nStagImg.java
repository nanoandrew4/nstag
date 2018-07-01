package nstag.img;

import nstag.Compressor;
import nstag.Spinner;
import nstag.nStag;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class nStagImg extends nStag {

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
		BufferedImage original = null;
		byte[] dataBytes;
		try {
			Spinner.printWithSpinner("\nLoading image to encode to... ");
			original = ImageIO.read(new File(origPath));
			Spinner.printWithSpinner("Loading file to encode... ");
			dataBytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
			if (original == null)
				System.err.println("Image could not be loaded... Check you entered the right pathname");
			else
				System.err.println("File to encode could not be loaded... Check you entered the right pathname");
			return;
		}

		boolean encrypt = false;
		Spinner.spin();
		System.out.print("Do you wish to encrypt the data? Y/N: ");
		if ("y".equalsIgnoreCase(in.nextLine()))
			encrypt = true;

		byte[] keyBytes = null;

		ImgEncoder ie = new ImgEncoder(original, bitsPerChannel);

		if (encrypt) {
			Spinner.printWithSpinner("Encrypting data... ");
			byte[][] keyAndDataBits = encrypt(dataBytes, null); // Contains key bits and encrypted data byte array, respectively
			keyBytes = keyAndDataBits[0]; // Key bits, encoded after file size and bits per channel, for later decryption
			dataBytes = keyAndDataBits[1];
		}

		Spinner.printWithSpinner("Compressing data... ");
		double origByteSize = dataBytes.length;
		dataBytes = Compressor.compress(dataBytes);
		Spinner.spin();
		System.out.println("Compressed data by " + String.format("%.2f", ((origByteSize - dataBytes.length) / origByteSize) * 100.0) + "%");

		/*
		 * If number of bits to encode is larger than number of bits that can be encoded in image, notify user and
		 * return. The image can hold: (pixels * 4 * (bits per channel)). Each pixel has 4 channels, alpha, red, green
		 * and blue.
		 */
		long requiredBits = SIZE_BITS_COUNT + dataBytes.length * 8 + (encrypt ? KEY_BITS_COUNT : 0);
		if (requiredBits > original.getWidth() * original.getHeight() * bitsPerChannel * 4) {
			System.err.println("Not enough space in image, consider allowing more bits");
			System.err.println("Required bits: " + requiredBits);
			System.err.println("Available bits: " + (original.getWidth() * original.getHeight() * bitsPerChannel * 4));
			return;
		}

		Spinner.printWithSpinner("Encoding metadata... ");
		// Bits representing the file size (encrypted or otherwise, depending on what the user chooses)
		byte[] fileSizeBitsArr = intToBitArray((int) origByteSize, SIZE_BITS_COUNT, false); // File size in bytes
		ie.encodeBits(fileSizeBitsArr);

		if (encrypt)
			ie.encodeBytes(keyBytes);

		Spinner.printWithSpinner("Encoding data to image... ");
		ie.encodeBytes(dataBytes);

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

		int fileSize = bitArrayToInt(id.readBits(32), false);

		byte[] keyBytes = null;
		if (encrypted)
			keyBytes = id.readBytes(KEY_BITS_COUNT / 8);

		byte[] dataBytes = id.readBytes(fileSize);

		Spinner.spin();
		System.out.println();
		Spinner.printWithSpinner("Decompressing data... ");
		dataBytes = Compressor.decompress(dataBytes, fileSize);

		if (encrypted) {
			Spinner.printWithSpinner("Decrypting data... ");
			dataBytes = decrypt(dataBytes, keyBytes, null);
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
