package nsteg.nsteg_utils;

import javax.validation.constraints.NotNull;

/**
 * Contains methods for transforming integers to bit arrays, and from bit arrays to integers.
 */
public abstract class BitByteConv {

	/**
	 * Converts a decimal number into an array of bits. Can handle both signed and unsigned binary numbers.
	 *
	 * @param b         Number to convert to array of bits
	 * @param numOfBits Number of bits in the number that is being converted
	 * @return Array of bits representing the decimal number passed as an argument, in signed or unsigned form
	 */
	public static byte[] intToBitArray(int b, int numOfBits) {
		byte[] bits = new byte[numOfBits];
		boolean neg = b < 0;
		if (neg)
			b = ~b; // NOT op, flips all the bits so it can be operated upon normally

		/*
		 * Break number down into binary. If number is negative, all 0's should be 1's, and viceversa. That is
		 * achieved with the modulo operation, by adding one and mod 2, the bits will be opposite what they should be,
		 * which is the desired output when a number is negative.
		 */
		for (int i = 0; i < numOfBits; i++) {
			bits[numOfBits - 1 - i] = (byte) ((b + (neg ? 1 : 0)) % 2);
			b >>>= 1;
		}
		return bits;
	}

	/**
	 * Converts an array of integers (containing exclusively binary numbers) to a decimal representation of itself.
	 * Can handle conversion of both signed and unsigned binary numbers. Powers of two are handled through bit-shifting,
	 * which is way faster than using Math.pow().
	 *
	 * @param bits   Array of bits to be converted to a decimal integer
	 * @param signed True for interpreting as a signed number, false to interpret as an unsigned number
	 * @return Integer representation of the array of bits
	 */
	public static int bitArrayToInt(@NotNull byte[] bits, boolean signed) {
		boolean neg = signed && bits[0] == 1;
		int b = neg ? -(1 << bits.length - 1) : 0;
		for (int i = (neg ? 1 : 0); i < bits.length; i++)
			if (bits[i] != 0)
				b += (1 << bits.length - 1 - i);

		return b;
	}
}
