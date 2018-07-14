package nsteg;

import nsteg.decoders.aud.AudioDecoder;
import nsteg.decoders.img.ImgDecoder;
import nsteg.encoders.aud.AudioEncoder;
import nsteg.encoders.img.ImgEncoder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
		runImgBenchmark();
		runAudBenchmark();
	}

	private static void printEncDecSpeed(long start, long finish, int dataLen, int bpc, boolean encode) {
		double speed = (dataLen / (1 << 20)) / ((finish - start) / 1000.0);
		System.out.println((encode ? "Encoding" : "Decoding") + " speed using " + bpc + " bits per channel is: " +
				String.format("%.2f", speed) + " MB/s"
		);
	}

	private static void runImgBenchmark() {
		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Image encoding/decoding benchmark");
		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Running benchmark on RGB image, using ~4MB of random data to encode.");
		System.out.println("Results are estimates since data is random, and CPU utilization is always changing.");
		System.out.println("Running this benchmark multiple times will skew results, since the JVM will be warmed up.");
		System.out.println("-------------------------------------------------------------------------------------------");

		BufferedImage img = new BufferedImage(5000, 5000, BufferedImage.TYPE_INT_RGB);
		for (int bpc = 1; bpc < 9; bpc++) {
			ImgEncoder ie = new ImgEncoder(img, bpc);

			byte[] dataSizeBits = new byte[32];
			ie.encodeBits(dataSizeBits);

			byte[] dataToEncode = new byte[1 << 22]; // ~4 MB
			Random rand = new Random();
			rand.nextBytes(dataToEncode);

			long start = System.currentTimeMillis();
			ie.encodeBytes(dataToEncode);
			long finish = System.currentTimeMillis();
			ie.stopThreads();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, true);

			ImgDecoder id = new ImgDecoder(img);
			id.readBits(dataSizeBits.length);

			start = System.currentTimeMillis();
			id.readBytes(dataToEncode.length);
			finish = System.currentTimeMillis();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, false);
			System.out.println();
		}
	}

	private static void runAudBenchmark() {
		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Audio encoding/decoding benchmark");
		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Although this benchmark may seem slower than the image benchmark, it is solely due");
		System.out.println("to the time it takes to generate a random noise audio file and read it into memory.");
		System.out.println("-------------------------------------------------------------------------------------------");

		byte[] audData = new byte[1 << 28];
		Random rand = new Random();
		rand.nextBytes(audData);

		AudioFormat af = new AudioFormat(44100, 16, 2, true, false);

		for (int bpc = 1; bpc < 9; bpc++) {
			AudioInputStream audioStream = new AudioInputStream(new ByteArrayInputStream(audData), af, audData.length);

			AudioEncoder ae = new AudioEncoder(audioStream, bpc);

			byte[] dataSizeBits = new byte[32];
			ae.encodeBits(dataSizeBits);

			byte[] dataToEncode = new byte[1 << 22];
			rand.nextBytes(dataSizeBits);

			long start = System.currentTimeMillis();
			ae.encodeBytes(dataToEncode);
			long finish = System.currentTimeMillis();
			ae.stopThreads();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, true);

			byte[] encData = ae.getEncodedPCM();

			AudioDecoder ad = new AudioDecoder(new AudioInputStream(new ByteArrayInputStream(encData), af, encData.length));
			ad.readBits(dataSizeBits.length);

			start = System.currentTimeMillis();
			ad.readBytes(dataToEncode.length);
			finish = System.currentTimeMillis();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, false);
			System.out.println();
		}

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
	}
}
