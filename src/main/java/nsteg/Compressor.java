package nsteg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class Compressor {
	public static byte[] compress(byte[] bytes) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.BEST_COMPRESSION, true));
		try {
			dos.write(bytes);
			dos.close();
		} catch (IOException e) {
			System.err.println("Compression failed");
		}

		return baos.size() < bytes.length ? baos.toByteArray() : bytes;
	}

	public static byte[] decompress(byte[] compBytes, int uncompSize) {
		if (compBytes.length >= uncompSize)
			return compBytes;

		ByteArrayInputStream bais = new ByteArrayInputStream(compBytes);
		InflaterInputStream iis = new InflaterInputStream(bais, new Inflater(true));

		byte[] uncompBytes = new byte[uncompSize];
		int pos = 0;
		try {
			while (pos < uncompSize)
				uncompBytes[pos++] = (byte) iis.read();
			System.out.println(pos + " " + uncompSize);
			iis.close();
		} catch (IOException e) {
			System.err.println("Decompression failed");
		}

		return uncompBytes;
	}
}
