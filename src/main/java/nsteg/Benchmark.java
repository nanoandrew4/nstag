package nsteg;

import nsteg.decoders.img.ImgDecoder;
import nsteg.encoders.img.ImgEncoder;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Benchmarks the image encoder and decoder, in order to determine what the throughput of it is on the system it is run
 * on.
 */
public class Benchmark {

	/**
	 * Runs the benchmark, and outputs the results. The benchmark consists of encoding ~4 MB of data into an image,
	 * using all possible bitsPerChannel values (1-8). Each bitsPerChannel iteration outputs its own throughput
	 * information.
	 */
	public static void run() {
		System.out.println("Running benchmark on RGB image, using ~4MB of random data to encode.");
		System.out.println("Results are estimates since data is random, and CPU utilization is always changing.");
		System.out.println("Running this benchmark multiple times will skew results, since the JVM will be warmed up.\n");

		BufferedImage img = new BufferedImage(5000, 5000, BufferedImage.TYPE_INT_RGB);
		for (int bpc = 1; bpc < 9; bpc++) {
			ImgEncoder ie = new ImgEncoder(img, bpc);

			byte[] dataSize = new byte[32];
			ie.encodeBits(dataSize);

			byte[] data = new byte[(int) Math.pow(2, 22)]; // ~4 MB
			Random rand = new Random();
			rand.nextBytes(data);

			long start = System.currentTimeMillis();
			ie.encodeBytes(data);
			long finish = System.currentTimeMillis();
			ie.stopThreads();

			double encodeSpeed = (data.length / (Math.pow(2, 20))) / ((finish - start) / 1000.0);
			System.out.println("Encoding speed using " + bpc + " bits per channel is: " +
					String.format("%.2f", encodeSpeed) + " MB/s"
			);

			ImgDecoder id = new ImgDecoder(img);
			start = System.currentTimeMillis();
			id.readBytes(data.length);
			finish = System.currentTimeMillis();

			double decodeSpeed = (data.length / (Math.pow(2, 20))) / ((finish - start) / 1000.0);
			System.out.println("Decoding speed using " + bpc + " bits per channel is: " +
					String.format("%.2f", decodeSpeed) + " MB/s"
			);
			System.out.println();
		}

		System.out.println();
	}
}
