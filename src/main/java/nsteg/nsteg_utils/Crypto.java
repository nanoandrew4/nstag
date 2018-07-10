package nsteg.nsteg_utils;

import com.lambdaworks.crypto.SCrypt;
import nsteg.Spinner;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Scanner;

public class Crypto {
	private static Scanner in = new Scanner(System.in);

	final public static int AES_IV_SIZE = 12; // 12 bytes, recommended for GCM
	final public static int GCM_AAD_SIZE = 16 * Byte.SIZE; // 128 bits
	final public static int SALT_SIZE_BITS = 8 * Byte.SIZE; // 64 bits

	final private static int keyLen = 32; // 256 bit AES key

	public static boolean offerToCrypt(boolean encrypt) {
		System.out.print("Do you wish to " + (encrypt ? "encrypt" : "decrypt") + " this data? Y/N: ");
		return "y".equalsIgnoreCase(in.nextLine());
	}

	/**
	 * Generates an array of 16 bytes to be used as additional associated data when encryption is performed. The
	 * compressed and uncompressed file size bits are used to create this 16 byte array. Only the second half of the
	 * size arrays are used.
	 *
	 * @param uncompSizeBits Array containing the bit representation of the uncompressed size of the array
	 * @param compSizeBits   Array containing the bit representation of the compressed size of the array
	 * @return 16 byte array to be used as AAD
	 */
	public static byte[] genAAD(byte[] uncompSizeBits, byte[] compSizeBits) {
		byte[] header = new byte[GCM_AAD_SIZE / Byte.SIZE];
		System.arraycopy(compSizeBits, compSizeBits.length - 8, header, 0, 8);
		System.arraycopy(uncompSizeBits, uncompSizeBits.length - 8, header, 8, 8);

		return header;
	}

	/**
	 * Encrypts an array of bytes using the AES-256 bit cipher. The key is derived from a user specified password,
	 * using scrypt to generate a 256 bit key, and using a random 64 bit salt. Additional associated data is also
	 * included to detect if the encrypted data has been tampered with. The AAD and IV are then combined with the
	 * encrypted data. The salt used to derive the key is returned alongside the encrypted data.
	 *
	 * @param bytesToEncrypt Byte array to encrypt
	 * @param aad            Associated data array (16 bytes) to detect data tampering with
	 * @return Two dimensional byte array of size two, containing the salt bytes and the encrypted bytes, respectively
	 */
	public static byte[][] encrypt(byte[] bytesToEncrypt, byte[] aad) {
		byte[][] saltAndCiphertext = new byte[2][];
		saltAndCiphertext[1] = bytesToEncrypt;

		System.out.print("Enter the password to use: ");

		Cipher cipher;
		byte[] encData;
		byte[] iv = new byte[AES_IV_SIZE];
		try {
			SecureRandom secureRandom = new SecureRandom();
			secureRandom.nextBytes(iv);

			saltAndCiphertext[0] = new byte[SALT_SIZE_BITS / Byte.SIZE]; // 64 bit salt
			secureRandom.nextBytes(saltAndCiphertext[0]);

			byte[] pass = in.nextLine().getBytes();
			Spinner.printWithSpinner("Encrypting data... ");
			byte[] key = SCrypt.scrypt(pass, saltAndCiphertext[0], (int) Math.pow(2, 18), 8, 8, keyLen);
			Arrays.fill(pass, (byte) 0);

			cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_AAD_SIZE, iv));
			Arrays.fill(key, (byte) 0);

			cipher.updateAAD(aad); // Add associated data, to prevent tampering with encrypted data
			encData = cipher.doFinal(bytesToEncrypt); // Encrypt
		} catch (GeneralSecurityException e) {
			System.err.println("Encryption failed");
			return saltAndCiphertext;
		}

		// Write IV and ciphertext to byte array, and encode that
		ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encData.length);
		byteBuffer.put(iv);
		byteBuffer.put(encData);

		saltAndCiphertext[1] = byteBuffer.array();

		return saltAndCiphertext;
	}

	/**
	 * Decrypts an array of bytes previously encrypted by this program, obtained from extracting the bits from the least
	 * significant bit(s) of an image. Decryption is attempted by deriving the key used to encrypt the data using scrypt,
	 * and the salt that was encoded alongside the encrypted data.
	 *
	 * @param bytesToDecrypt Array of encrypted bytes to be decrypted
	 * @param aad            Associated data used to verify the encrypted data was not tampered with
	 * @param salt           Salt used to hash the password
	 * @return Decrypted array of bytes
	 */
	public static byte[] decrypt(byte[] bytesToDecrypt, byte[] salt, byte[] aad) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(bytesToDecrypt);
		byte[] iv = new byte[AES_IV_SIZE];
		byteBuffer.get(iv);

		byte[] cipherText = new byte[byteBuffer.remaining()];
		byteBuffer.get(cipherText);

		Cipher cipher;
		byte[] unencData;
		try {
			cipher = Cipher.getInstance("AES/GCM/NoPadding");

			System.out.print("\nEnter password: ");
			byte[] pass = in.nextLine().getBytes();

			Spinner.printWithSpinner("Decrypting data... ");
			byte[] key = SCrypt.scrypt(pass, salt, (int) Math.pow(2, 18), 8, 8, keyLen);
			Arrays.fill(pass, (byte) 0);

			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_AAD_SIZE, iv));
			Arrays.fill(key, (byte) 0);

			cipher.updateAAD(aad); // Verify data was not tampered with
			unencData = cipher.doFinal(cipherText); // Decrypt
		} catch (GeneralSecurityException e) {
			System.err.println("Decryption failed");
			return bytesToDecrypt;
		}

		return unencData;
	}
}
