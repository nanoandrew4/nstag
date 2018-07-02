package nsteg;

import com.lambdaworks.crypto.SCrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Scanner;

public abstract class nSteg {
	protected static Scanner in = new Scanner(System.in);

	final protected static int SALT_SIZE_BITS = 8 * 8;
	final protected static int SIZE_BITS_COUNT = 4 * 8;

	final protected static int GCM_TAG_SIZE = 128; // 16 bytes
	final protected static int AES_IV_SIZE = 12; // 12 bytes

	final private static int keyLen = 32; // 256 bit

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
	 * Encrypts an array of bytes using the AES-256 bit cipher. The key used as part of the encryption (which is
	 * required for decryption later) is converted to an array of bits and forms the first element of the two
	 * dimensional array that is returned. The second element of the array returned is the encrypted byte array
	 * representing the file that is being encoded in the image.
	 *
	 * @param bytesToEncrypt Byte array to encrypt
	 * @return Two dimensional byte array of size two, containing the key bits and the encrypted bytes, respectively
	 */
	protected static byte[][] encrypt(byte[] bytesToEncrypt, byte[] header) {
		byte[][] saltAndCiphertext = new byte[2][];
		saltAndCiphertext[1] = bytesToEncrypt;

		System.out.print("Enter the password to use: ");

		Cipher cipher;
		byte[] encData;
		byte[] iv = new byte[AES_IV_SIZE]; // Initialization vector, 12 bytes is GCM default, which is also the most secure
		try {
			SecureRandom secureRandom = new SecureRandom();
			secureRandom.nextBytes(iv);

			cipher = Cipher.getInstance("AES/GCM/NoPadding");

			saltAndCiphertext[0] = new byte[8]; // 64 bit salt
			secureRandom.nextBytes(saltAndCiphertext[0]);
			byte[] pass = in.nextLine().getBytes();
			Spinner.printWithSpinner("Encrypting data... ");
			byte[] key = SCrypt.scrypt(pass, saltAndCiphertext[0], (int) Math.pow(2, 18), 8, 8, keyLen);
			Arrays.fill(pass, (byte) 0);

			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_SIZE, iv));
			Arrays.fill(key, (byte) 0);

			cipher.updateAAD(header); // Add header as associated data, to prevent tampering with encrypted data
			encData = cipher.doFinal(bytesToEncrypt);
		} catch (GeneralSecurityException e) {
			System.err.println("Cipher creation failed...");
			e.printStackTrace();
			return saltAndCiphertext;
		}

//		Write IV and ciphertext to byte array, and encode that
		ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encData.length);
		byteBuffer.put(iv);
		byteBuffer.put(encData);

		saltAndCiphertext[1] = byteBuffer.array();

		return saltAndCiphertext;
	}

	/**
	 * Decrypts an array of bytes previously encrypted by this program, obtained from extracting the bits from the least
	 * significant bit(s) of an image. Decryption is attempted using the key stored alongside the encrypted data,
	 * and with the password requested from the user. The decrypted byte array is then returned.
	 *
	 * @param bytesToDecrypt Array of encrypted bytes to be decrypted
	 * @param salt           Salt used to hash the password
	 * @return Decrypted array of bytes
	 */
	protected static byte[] decrypt(byte[] bytesToDecrypt, byte[] header, byte[] salt) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(bytesToDecrypt);
		byte[] iv = new byte[AES_IV_SIZE];
		byteBuffer.get(iv);
		byte[] cipherText = new byte[byteBuffer.remaining()];
		byteBuffer.get(cipherText);

		System.out.print("Enter password: ");
		byte[] pass = in.nextLine().getBytes();

		Spinner.printWithSpinner("Decrypting data... ");

		Cipher cipher;
		byte[] unencData;
		try {
			cipher = Cipher.getInstance("AES/GCM/NoPadding");
			byte[] key = SCrypt.scrypt(pass, salt, (int) Math.pow(2, 18), 8, 8, keyLen);
			Arrays.fill(pass, (byte) 0);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_SIZE, iv));

			Arrays.fill(key, (byte) 0);
			cipher.updateAAD(header);
			unencData = cipher.doFinal(cipherText);
		} catch (GeneralSecurityException e) {
			System.err.println("Cipher creation failed...");
			e.printStackTrace();
			return bytesToDecrypt;
		}

		return unencData;
	}
}