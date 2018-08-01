package nsteg.nsteg_utils;

import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class Compressor {

	/**
	 * Attempts to compress an array of bytes. If the compressed size is larger than the original, the original array
	 * is returned. Otherwise returns the compressed array.
	 *
	 * @param bytes Array of bytes to be compressed
	 * @return Original array or compressed array, whichever is smaller
	 */
	public static byte[] compress(@NotNull byte[] bytes) {
		Spinner.printWithSpinner("Compressing data... ");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.BEST_COMPRESSION, true));
		try {
			dos.write(bytes);
			dos.close();
		} catch (IOException e) {
			System.err.println("Compression failed");
		}

		Spinner.end();
		System.out.println(
				"Compressed data by " + String.format("%.2f", ((bytes.length - baos.size()) / (double) bytes.length) * 100.0) + "%"
		);

		return baos.size() < bytes.length ? baos.toByteArray() : bytes;
	}

	/**
	 * Attempts to decompress an array, if it is smaller than the original array. Otherwise, returns the passed array,
	 * since it was not compressed.
	 *
	 * @param compBytes  Bytes to be decompressed, if they were compressed in the first place
	 * @param uncompSize Size of the uncompressed file, to check if the data was compressed, and to determine how many
	 *                   bytes to read
	 * @return Array containing uncompressed data
	 */
	public static byte[] decompress(@NotNull byte[] compBytes, int uncompSize) {
		if (compBytes.length >= uncompSize)
			return compBytes;

		Spinner.end();
		System.out.println();
		Spinner.printWithSpinner("Decompressing data... ");

		ByteArrayInputStream bais = new ByteArrayInputStream(compBytes);
		InflaterInputStream iis = new InflaterInputStream(bais, new Inflater(true));

		byte[] uncompBytes = new byte[uncompSize];
		try {
			int pos = 0;
			while (pos < uncompSize)
				uncompBytes[pos++] = (byte) iis.read();
			iis.close();
		} catch (IOException e) {
			System.err.println("Decompression failed");
		}

		return uncompBytes;
	}
}
