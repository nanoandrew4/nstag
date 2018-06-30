package nstag;

import com.google.crypto.tink.*;
import com.google.crypto.tink.aead.AeadFactory;
import com.google.crypto.tink.aead.AeadKeyTemplates;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Scanner;

public abstract class nStag {
	protected static Scanner in = new Scanner(System.in);

	final protected static int KEY_BITS_COUNT = 92 * 8;
	final protected static int SIZE_BITS_COUNT = 4 * 8;

	protected static byte[] byteToBitArray(byte[] origArr) {
		byte[] bitArr = new byte[origArr.length * 8];

		int pos = 0;
		byte[] byteBitArr;

		for (byte bite : origArr) {
			byteBitArr = intToBitArray(bite, 8, true);
			for (byte bit : byteBitArr)
				bitArr[pos++] = bit;
		}

		return bitArr;
	}

	/**
	 * Converts a decimal number into an array of bits. Can handle both signed and unsigned binary numbers.
	 *
	 * @param b         Number to convert to array of bits
	 * @param numOfBits Number of bits in the number that is being converted
	 * @param signed    True for interpreting as a signed number, false for interpreting as an unsigned number
	 * @return Array of bits representing the decimal number passed as an argument, in signed or unsigned form
	 */
	public static byte[] intToBitArray(int b, int numOfBits, boolean signed) {
		byte[] bits = new byte[numOfBits];
		boolean neg = b < 0;
		if (signed && neg) {
			b = ~b; // NOT op, flips all the bits so it can be operated upon normally
			bits[0] = 1; // Set first bit to indicate the bits represent a negative number
		}

		/*
		 * Break number down into binary. If number is negative, all 0's should be 1's, and viceversa. That is
		 * achieved with the modulo operation, by adding one and mod 2, the bits will be opposite what they should be,
		 * which is the desired output when a number is negative.
		 */
		for (int i = 0; i < numOfBits; i++) {
			bits[numOfBits - 1 - i] = (byte) ((b + (signed && neg ? 1 : 0)) % 2);
			b >>>= 1;
		}
		return bits;
	}

	/**
	 * Converts an array of integers (containing exclusively binary numbers) to a decimal representation of itself.
	 * Can handle conversion of both signed and unsigned binary numbers.
	 *
	 * @param bits   Array of bits to be converted to a decimal integer
	 * @param signed True for interpreting as a signed number, false to interpret as an unsigned number
	 * @return Integer representation of the array of bits
	 */
	public static int bitArrayToInt(byte[] bits, boolean signed) {
		boolean neg = signed && bits[0] == 1;
		int b = signed && neg ? -128 : 0;
		for (int i = (signed && neg ? 1 : 0); i < bits.length; i++)
			if (bits[i] != 0)
				b += Math.pow(2, bits.length - 1 - i);
		return b;
	}

	/**
	 * Encrypts an array of bytes using the AES-128 bit cipher. The key used as part of the encryption (which is
	 * required for decryption later) is converted to an array of bits and forms the first element of the two
	 * dimensional array that is returned. The second element of the array returned is the encrypted byte array
	 * representing the file that is being encoded in the image.
	 *
	 * @param bytesToEncrypt Byte array to encrypt
	 * @return Two dimensional byte array of size two, containing the key bits and the encrypted bytes, respectively
	 */
	protected static byte[][] encrypt(byte[] bytesToEncrypt) {
		byte[][] keyAndData = new byte[2][];
		keyAndData[1] = bytesToEncrypt;

		KeysetHandle keysetHandle;
		try {
			keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES128_GCM);
			Aead aead = AeadFactory.getPrimitive(keysetHandle);

			// Writes key to start of bit array, after encryption, otherwise decrypting the data would not be possible
			keyAndData[0] = writeKeyToBitDeque(keysetHandle);

			// Generate password hash to be used to encrypt the data
			Spinner.spin();
			System.out.print("Please enter the password to use: ");

			// Encrypt data and generate seed for scrambling the bits later
			keyAndData[1] = aead.encrypt(bytesToEncrypt, in.nextLine().getBytes());
			Spinner.spin();
		} catch (GeneralSecurityException e) {
			System.err.println("Encryption failed, aborting...");
			return keyAndData;
		}

		return keyAndData;
	}

	/*
	 * Writes the AES key from the given KeysetHandle to a byte array and then converts it to a bit array, which is
	 * returned to later be written. This key is used to encrypt and decrypt the data that has been passed to the caller
	 * of this method.
	 */
	private static byte[] writeKeyToBitDeque(KeysetHandle keysetHandle) {
		try {
			byte[] key;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			CleartextKeysetHandle.write(keysetHandle, BinaryKeysetWriter.withOutputStream(baos));
			key = baos.toByteArray();
			return key;
		} catch (IOException e) {
			System.err.println("Error obtaining key, no encryption will be performed, aborting...");
			return null;
		}
	}

	/**
	 * Decrypts an array of bytes previously encrypted by this program, obtained from extracting the bits from the least
	 * significant bit(s) of an image. Decryption is attempted using the key stored alongside the encrypted data,
	 * and with the password requested from the user. The decrypted byte array is then returned.
	 *
	 * @param bytesToDecrypt Array of encrypted bytes to be decrypted
	 * @param key            Key the array was originally encrypted with
	 * @return Decrypted array of bytes
	 */
	protected static byte[] decrypt(byte[] bytesToDecrypt, byte[] key) {
		System.out.print("Please enter the password to use: ");
		KeysetHandle keysetHandle;

		try {
			keysetHandle = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(key));
			Aead aead = AeadFactory.getPrimitive(keysetHandle);

			// Encrypt data and generate seed for scrambling the bits later
			Spinner.spin();
			return aead.decrypt(bytesToDecrypt, in.nextLine().getBytes());
		} catch (GeneralSecurityException | IOException e) {
			System.err.println("Encryption failed, aborting...");
			return bytesToDecrypt;
		}
	}
}
