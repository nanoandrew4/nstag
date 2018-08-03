package nsteg.main;

import nsteg.decoders.aud.AudDecoder;
import nsteg.decoders.img.ImgDecoder;
import nsteg.encoders.aud.AudEncoder;
import nsteg.encoders.img.ImgEncoder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Random;

/**
 * Benchmarks the image and audio encoders/decoders. The benchmarks display the encoding/decoding speeds in MiB/s.
 */
public class Benchmark {

	/**
	 * Runs the benchmarks, and outputs the results. The benchmarks consists of encoding ~4 MiB of data into an image
	 * and audio file, using all possible bitsPerChannel values (1-8). Each bitsPerChannel iteration outputs its own
	 * throughput information.
	 */
	public static void run() {
		runImgBenchmark();
		runAudBenchmark();
	}

	private static void printEncDecSpeed(long start, long finish, int dataLen, int bpc, boolean encode) {
		double speed = ((double) dataLen / (1 << 20)) / ((finish - start) / 1000.0);
		System.out.println((encode ? "Encoding" : "Decoding") + " speed using " + bpc + " bits per channel is: " +
				String.format("%.2f", speed) + " MiB/s"
		);
	}

	private static void runImgBenchmark() {
		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Image encoding/decoding benchmark");
		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Running benchmark on RGB image, using ~4MiB of random data to encode.");
		System.out.println("Results are estimates since data is random, and CPU utilization is always changing.");
		System.out.println("Running this benchmark multiple times will skew results, since the JVM will be warmed up.");
		System.out.println("-------------------------------------------------------------------------------------------");

		BufferedImage img = new BufferedImage(5000, 5000, BufferedImage.TYPE_INT_RGB);
		for (int bpc = 1; bpc < 9; bpc++) {
			ImgEncoder ie = new ImgEncoder(img, bpc);

			byte[] dataSizeBits = new byte[32];
			ie.encodeBits(dataSizeBits); // Size bits are encoded to simulate a real encoding process
			ie.encodeBits(dataSizeBits);

			byte[] dataToEncode = new byte[1 << 22]; // 4 MiB of random data
			Random rand = new Random(0);
			rand.nextBytes(dataToEncode);

			long start = System.currentTimeMillis();
			ie.encodeBytes(dataToEncode);
			ie.stopThreads();
			long finish = System.currentTimeMillis();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, true);

			ImgDecoder id = new ImgDecoder(img);
			id.readBits(dataSizeBits.length);
			id.readBits(dataSizeBits.length);

			start = System.currentTimeMillis();
			id.readBytes(dataToEncode.length);
			id.stopThreads();
			finish = System.currentTimeMillis();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, false);
			System.out.println();
		}
	}

	private static void runAudBenchmark() {
		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("Audio encoding/decoding benchmark");
		System.out.println("-------------------------------------------------------------------------------------------");
		System.out.println("Running benchmark on random noise, using ~4MiB of random data to encode.");
		System.out.println("Although this benchmark may seem slower than the image benchmark, it is solely due");
		System.out.println("to the time it takes to generate a random noise audio file and read it into memory.");
		System.out.println("-------------------------------------------------------------------------------------------");

		byte[] audData = new byte[1 << 27]; // Use 134 MB of random noise, in order to fit all bpc iterations
		Random rand = new Random(0);
		rand.nextBytes(audData);

		AudioFormat af = new AudioFormat(44100, 16, 2, true, false);

		for (int bpc = 1; bpc < 9; bpc++) {
			AudioInputStream audioStream = new AudioInputStream(new ByteArrayInputStream(audData), af, audData.length);

			AudEncoder ae = new AudEncoder(audioStream, bpc);

			byte[] dataSizeBits = new byte[32];
			ae.encodeBits(dataSizeBits); // Size bits are encoded to simulate a real encoding process
			ae.encodeBits(dataSizeBits);

			byte[] dataToEncode = new byte[1 << 22]; // 4 MiB of random data
			rand.nextBytes(dataSizeBits);

			long start = System.currentTimeMillis();
			ae.encodeBytes(dataToEncode);
			ae.stopThreads();
			long finish = System.currentTimeMillis();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, true);

			byte[] encData = ae.getEncodedPCM();

			AudDecoder ad = new AudDecoder(new AudioInputStream(new ByteArrayInputStream(encData), af, encData.length));
			ad.readBits(dataSizeBits.length);
			ad.readBits(dataSizeBits.length);

			start = System.currentTimeMillis();
			ad.readBytes(dataToEncode.length);
			ad.stopThreads();
			finish = System.currentTimeMillis();

			printEncDecSpeed(start, finish, dataToEncode.length, bpc, false);

			if (bpc != 8)
				System.out.println();
		}

		System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
	}
}
