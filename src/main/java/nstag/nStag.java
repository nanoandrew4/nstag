package nstag;

import com.google.crypto.tink.*;
import com.google.crypto.tink.aead.AeadFactory;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.lambdaworks.crypto.SCryptUtil;

import java.io.*;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.*;

public abstract class nStag {
	protected static Scanner in = new Scanner(System.in);

	protected static String origPath, fileToHide, outPath;

	/**
	 * Converts a decimal number into an array of bits. Can handle both signed and unsigned binary numbers.
	 *
	 * @param b         Number to convert to array of bits
	 * @param numOfBits Number of bits in the number that is being converted
	 * @param signed    True for interpreting as a signed number, false for interpreting as an unsigned number
	 * @return Array of bits representing the decimal number passed as an argument, in signed or unsigned form
	 */
	protected static int[] intToBitArray(int b, int numOfBits, boolean signed) {
		int[] bits = new int[numOfBits];
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
			bits[numOfBits - 1 - i] = (b + (signed && neg ? 1 : 0)) % 2;
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
	protected static long bitArrayToLong(int[] bits, boolean signed) {
		boolean neg = signed && bits[0] == 1;
		int b = signed && neg ? -128 : 0;
		for (int i = (signed && neg ? 1 : 0); i < bits.length; i++)
			if (bits[i] != 0)
				b += Math.pow(2, bits.length - 1 - i);
		return b;
	}

	protected void requestInput(String fileType, boolean encode) {
		if (encode)
			System.out.print("Path to " + fileType + " to hide data in: ");
		else
			System.out.print("Path to " + fileType + " to decode data from: ");
		origPath = in.nextLine();
		if (encode) {
			System.out.print("Path to file to be hidden: ");
			fileToHide = in.nextLine();
		}
		if (encode)
			System.out.print("Desired path and filename (with extension) for output " + fileType + ": ");
		else
			System.out.print("Desired path and filename (with extension) for output decoded file: ");
		outPath = in.nextLine();
	}

	protected static void offerToEncrypt(ArrayDeque<Integer> unencBits) {
		System.out.print("Do you want to encrypt the data? Y/N: ");
		String ans = in.nextLine();

		if ("n".equalsIgnoreCase(ans))
			return ;

		KeysetHandle keysetHandle;
		Aead aead;

		byte[] encryptedData;
		long seed;
		try {
			keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES128_GCM);
			aead = AeadFactory.getPrimitive(keysetHandle);

			if (!writeKeyToBitDeque(unencBits, keysetHandle))
				return;

			System.out.print("Please enter the password to use: ");
			String pass = SCryptUtil.scrypt(in.nextLine(), (int) Math.pow(2, 18), 16, 2);
			System.out.println("Scrypt hash: " + pass);

			Integer[] unencBitArray = unencBits.toArray(new Integer[unencBits.size()]);
			byte[] dataToEnc = new byte[unencBits.size()];
			for (int i = 0; i < dataToEnc.length; i++)
				dataToEnc[i] = Byte.valueOf(Integer.toString(unencBitArray[i])); // ew

			encryptedData = aead.encrypt(dataToEnc, pass.getBytes()); // TODO: MAYBE HASH SEED, THEN HASH UNIQUE STR FOR PASS?
			seed = genSeed(SCryptUtil.scrypt(pass, (int) Math.pow(2, 16), 16, 2));
		} catch (GeneralSecurityException e) {
			System.err.println("Encryption failed, aborting...");
			return;
		}

		shuffleBits(unencBits, encryptedData, seed);
	}

	private static boolean writeKeyToBitDeque(ArrayDeque<Integer> unencBits, KeysetHandle keysetHandle) {
		try {
			byte[] key;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			CleartextKeysetHandle.write(keysetHandle, BinaryKeysetWriter.withOutputStream(baos));
			key = baos.toByteArray();
			Stack<Integer> keyBits = new Stack<>();

			for (int keyByte : key) {
				int[] kBits = intToBitArray(keyByte, 8, true);
				for (int b = kBits.length - 1; b >= 0; b--)
					keyBits.add(kBits[b]);
			}

			while (!keyBits.empty())
				unencBits.addFirst(keyBits.pop());
			return true;
		} catch (IOException e) {
			System.err.println("Error obtaining key, no encryption will be performed, aborting...");
			return false;
		}
	}

	private static void shuffleBits(ArrayDeque<Integer> unencBits, byte[] encryptedData, long seed) {
		ArrayList<Integer> newPositions = new ArrayList<>();
		for (int i = 0; i < unencBits.size(); i++)
			newPositions.add(i);
		Collections.shuffle(newPositions, new Random(seed));

		ArrayList<Integer> shuffledBits = new ArrayList<>();
		for (int i = 0; i < unencBits.size(); i++)
			shuffledBits.set(newPositions.get(i), (int) encryptedData[i]);

		unencBits.clear();
		unencBits.addAll(shuffledBits);
	}

	private static long genSeed(String pass) {
		BigInteger num = new BigInteger("0");
		for (int i = 0; i < pass.length(); i++)
			num = num.add(new BigInteger("" + ((int) pass.charAt(i) * Math.pow(128, i))));
		num = num.add(new BigInteger("" + (pass.length() * Math.pow(128, 2 * pass.length()))));

		int[] init = new int[63];
		for (int i = 0; i < 63; i++)
			init[i] = num.toString().charAt(i);

		Random rand = new Random(bitArrayToLong(init, false));

		int randBitPos = rand.nextInt() % (num.toString().length() - 64);

		int[] seedBits = new int[63];
		for (int i = 0; i < 63; i++)
			seedBits[randBitPos + i] = num.toString().charAt(i);
		return bitArrayToLong(seedBits, false);
	}

	public static byte[] offerToDecrypt(byte[] bytes) {
		System.out.print("Is this file encrypted? Y/N: ");
		String ans = in.nextLine();

		if ("y".equalsIgnoreCase(ans)) {
			KeysetHandle keysetHandle;
			Aead aead;
			try {
				keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES128_GCM);
				aead = AeadFactory.getPrimitive(keysetHandle);

				System.out.print("Please enter the password: ");
				return aead.decrypt(bytes, "testing".getBytes());
			} catch (GeneralSecurityException e) {
				System.err.println("Decryption failed");
			}
		}

		return bytes;
	}
}
