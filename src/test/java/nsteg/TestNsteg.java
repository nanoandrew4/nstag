package nsteg;

import nsteg.img.decoder.ImgDecoder;
import nsteg.img.encoder.ImgEncoder;
import nsteg.nsteg_utils.BitByteConv;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TestNsteg {

	@Test
	public void testEncDec() {
		for (int i = 0; i < 2; i++) {
			int imgType = (i == 0 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_4BYTE_ABGR);
			for (int bpc = 1; bpc < 9; bpc++) {
				BufferedImage img = new BufferedImage(1000, 1000, imgType);
				byte[] data = new byte[(int) Math.pow(2, 13)]; // ~8kb

				Random rand = new Random();
				rand.nextBytes(data);

				ImgEncoder ie = new ImgEncoder(img, bpc);
				ie.encodeBits(BitByteConv.intToBitArray(data.length, 32));
				ie.encodeBytes(data);
				ie.stopThreads();

				ImgDecoder id = new ImgDecoder(img);
				byte[] fSizeBits = id.readBits(32);
				int bytesToRead = BitByteConv.bitArrayToInt(fSizeBits, false);

				byte[] decData = id.readBytes(bytesToRead);

				assertArrayEquals(data, decData);

				System.out.println("Passed bpc " + bpc + " using " + (imgType == BufferedImage.TYPE_4BYTE_ABGR ? "A" : "") + "RGB");
			}
			System.out.println();
		}
	}

	@Test
	public void testBitByteConv() {
		for (int i = Integer.MAX_VALUE - 1000000; i < Integer.MAX_VALUE; i++) {
			byte[] bits = BitByteConv.intToBitArray(i, 32);
			assertEquals(i, BitByteConv.bitArrayToInt(bits, false));
		}

		for (int i = Integer.MIN_VALUE; i < Integer.MIN_VALUE + 1000000; i++) {
			byte[] bits = BitByteConv.intToBitArray(i, 32);
			assertEquals(i, BitByteConv.bitArrayToInt(bits, true));
		}
	}
}
