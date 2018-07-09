package nsteg;

import nsteg.img.encoder.ImgEncoder;

import java.awt.image.BufferedImage;
import java.util.Random;

public class Benchmark {
	public static void run() {
		System.out.println("Running benchmark on RGB image...\n");

		BufferedImage img = new BufferedImage(5000, 5000, BufferedImage.TYPE_INT_RGB);
		for (int bpc = 1; bpc < 9; bpc++) {
			ImgEncoder ie = new ImgEncoder(img, bpc);

			byte[] dataSize = new byte[32];
			ie.encodeBits(dataSize);

			byte[] data = new byte[(int) Math.pow(2, 20)]; // ~1 MB
			Random rand = new Random();
			rand.nextBytes(data);

			long start = System.currentTimeMillis();
			ie.encodeBytes(data);
			long finish = System.currentTimeMillis();

			double encodeSpeed = (data.length / (Math.pow(2, 20))) / ((finish - start) / 1000.0);
			System.out.println("Encoding speed using " + bpc + " bits per channel is: " + String.format("%.2f", encodeSpeed) + " MB/s");
		}

		System.out.println();
	}
}
