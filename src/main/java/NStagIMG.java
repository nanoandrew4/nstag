import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;

public class NStagIMG {

	public static void encode(String origPath, String fileToEncode, String outName, int bitsToUse) {
		byte[] bytes;
		BufferedImage original;
		try {
			original = ImageIO.read(new File(origPath));
			bytes = Files.readAllBytes(Paths.get(fileToEncode));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		if (bytes.length * 8 > original.getWidth() * original.getHeight() * bitsToUse * 4) {
			System.err.println("Not enough space in image, consider allowing more bits");
			System.err.println("Required bits: " + (bytes.length * 8));
			System.err.println("Available bits: "+ (original.getWidth() * original.getHeight() * bitsToUse * 4));
			return;
		}

		System.out.print("Encoding... ");
		Spinner.spin();

		ArrayDeque<Integer> bits = new ArrayDeque<>();
		int[] bitsArr;
		for (byte aByte : bytes) {
			bitsArr = getBits(aByte, 8);
			for (int aBitsArr : bitsArr) bits.add(aBitsArr);
		}

		BufferedImage out = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);

		ArrayDeque<Integer> encodingData = new ArrayDeque<>();
		int[] fileSizeBitsArr = getBits(bits.size(), 32);
		for (int aFileSizeBitsArr : fileSizeBitsArr) encodingData.add(aFileSizeBitsArr);
		int[] bitsUsedArr = getBits(bitsToUse, 4);
		for (int aBitsUsedArr : bitsUsedArr) encodingData.add(aBitsUsedArr);

		for (int b = 0; b < 9; b++)
			out.setRGB(b, 0, insertDataToPixel(original.getRGB(b, 0), 1, encodingData));

		for (int j = 0; j < original.getHeight(); j++)
			for (int i = j == 0 ? 9 : 0; i < original.getWidth(); i++) {
				if (bits.size() > 0)
					out.setRGB(i, j, insertDataToPixel(original.getRGB(i, j), bitsToUse, bits));
				else
					out.setRGB(i, j, original.getRGB(i, j));
			}

		try {
			ImageIO.write(out, "png", new File(outName));
			Spinner.spin();
			System.out.println("Data encoded successfully into image: \"" + outName + "\"\n\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static int insertDataToPixel(int orig, int bitsToUse, ArrayDeque<Integer> bits) {
		int[] aBits = getBits(((orig >> 24) & 0xff), 8);
		int[] rBits = getBits(((orig >> 16) & 0xff), 8);
		int[] gBits = getBits(((orig >> 8) & 0xff), 8);
		int[] bBits = getBits((orig & 0xff), 8);

		for (int bit = 0; bit < bitsToUse && !bits.isEmpty(); bit++) {
			aBits[7 - bit] = bits.pop();
			rBits[7 - bit] = bits.pop();
			gBits[7 - bit] = bits.pop();
			bBits[7 - bit] = bits.pop();
		}

		return (toByte(aBits) << 24) | (toByte(rBits) << 16) | (toByte(gBits) << 8) | toByte(bBits);
	}

	private static int[] getBits(int b, int numOfBits) {
		int[] bits = new int[numOfBits];
		for (int i = 0; i < numOfBits && b > 0; i++) {
			bits[numOfBits - 1 - i] = b % 2;
			b >>>= 1;
		}
		return bits;
	}

	private static int toByte(int[] bits) {
		int b = 0;
		for (int i = bits.length - 1; i >= 0; i--)
			if (bits[bits.length - 1 - i] != 0)
				b += Math.pow(2, i);
		return b;
	}

	public static void decode(String encodedPath, String outFileName) {
		BufferedImage encoded;
		try {
			encoded = ImageIO.read(new File(encodedPath));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		System.out.print("Decoding... ");
		Spinner.spin();

		int[] imgSize = new int[32];
		for (int i = 0; i < 8; i++) {
			int p = encoded.getRGB(i, 0);
			imgSize[i * 4] = getBits(((p >> 24) & 0xff), 8)[7];
			imgSize[i * 4 + 1] = getBits(((p >> 16) & 0xff), 8)[7];
			imgSize[i * 4 + 2] = getBits(((p >> 8) & 0xff), 8)[7];
			imgSize[i * 4 + 3] = getBits((p & 0xff), 8)[7];
		}
		int bitsInImage = toByte(imgSize);

		int[] bitsEncoded = new int[4];
		int p = encoded.getRGB(8, 0);
		bitsEncoded[0] = getBits(((p >> 24) & 0xff), 8)[7];
		bitsEncoded[1] = getBits(((p >> 16) & 0xff), 8)[7];
		bitsEncoded[2] = getBits(((p >> 8) & 0xff), 8)[7];
		bitsEncoded[3] = getBits((p & 0xff), 8)[7];
		int bitsPerPixel = toByte(bitsEncoded);

		ArrayDeque<Integer> bits = new ArrayDeque<>();
		for (int j = 0; j < encoded.getHeight(); j++)
			for (int i = j == 0 ? 9 : 0; i < encoded.getWidth() && (i + j * encoded.getWidth() - 9) * 4 < bitsInImage; i++) {
				extractDataFromPixel(encoded.getRGB(i, j), bitsPerPixel, bits);
			}
		byte[] decodedBytes = new byte[bits.size() / 8];
		for (int b = 0; b < decodedBytes.length; b++) {
			int[] byteBits = {bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop(), bits.pop()};
			decodedBytes[b] = (byte) toByte(byteBits);
		}

		try {
			FileOutputStream fos = new FileOutputStream(outFileName);
			fos.write(decodedBytes);
			fos.close();
			Spinner.spin();
			System.out.println("Data decoded successfully into file: \"" + outFileName + "\"\n\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void extractDataFromPixel(int orig, int bitsToUse, ArrayDeque<Integer> bits) {
		int[] aBits = getBits(((orig >> 24) & 0xff), 8);
		int[] rBits = getBits(((orig >> 16) & 0xff), 8);
		int[] gBits = getBits(((orig >> 8) & 0xff), 8);
		int[] bBits = getBits((orig & 0xff), 8);

		for (int bit = 0; bit < bitsToUse; bit++) {
			bits.add(aBits[7 - bit]);
			bits.add(rBits[7 - bit]);
			bits.add(gBits[7 - bit]);
			bits.add(bBits[7 - bit]);
		}
	}
}
