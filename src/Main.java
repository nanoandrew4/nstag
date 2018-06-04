import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;

public class Main {
	public static void main(String... args) {
		BufferedImage ogImage;
		try {
			ogImage = ImageIO.read(new File("/home/nanoandrew4/IdeaProjects/nstag/test.png"));
			byte[] bytes = Files.readAllBytes(Paths.get("/home/nanoandrew4/IdeaProjects/nstag/text.txt"));
			encode(ogImage, 1, bytes);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		try {
			BufferedImage encImage = ImageIO.read(new File("/home/nanoandrew4/IdeaProjects/nstag/out.png"));
			decode(encImage, "outtext.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("You can fit: " + (ogImage.getWidth() * ogImage.getHeight() * 4) + " bits, using only the least significant bit");
	}

	private static void encode(BufferedImage original, int bitsToUse, byte[] bytes) {
		if (bytes.length * 8 > original.getWidth() * original.getHeight() * bitsToUse) {
			System.out.println("Not enough space in image, consider allowing more bits");
			return;
		}

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
			ImageIO.write(out, "png", new File("/home/nanoandrew4/IdeaProjects/nstag/out.png"));
			System.out.println("Image written, data encoded successfully");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static int insertDataToPixel(int orig, int bitsToUse, ArrayDeque<Integer> bits) {
		int[] aBits = getBits(((orig >> 24) & 0xff), 8);
		int[] rBits = getBits(((orig >> 16) & 0xff), 8);
		int[] gBits = getBits(((orig >> 8) & 0xff), 8);
		int[] bBits = getBits((orig & 0xff), 8);

		for (int bit = 0; bit < bitsToUse; bit++) {
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

	private static void decode(BufferedImage encoded, String fileName) {
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
			FileOutputStream fos = new FileOutputStream(fileName);
			fos.write(decodedBytes);
			fos.close();
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