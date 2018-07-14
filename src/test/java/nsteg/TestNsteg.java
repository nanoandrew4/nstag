package nsteg;

import nsteg.decoders.Decoder;
import nsteg.decoders.aud.AudioDecoder;
import nsteg.decoders.img.ImgDecoder;
import nsteg.encoders.Encoder;
import nsteg.encoders.aud.AudioEncoder;
import nsteg.encoders.img.ImgEncoder;
import nsteg.nsteg_utils.BitByteConv;
import org.junit.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TestNsteg {
	private byte[] genRandData(int bytes) {
		byte[] data = new byte[bytes];
		Random rand = new Random();
		rand.nextBytes(data);

		return data;
	}

	@Test
	public void testImgEncDec() {
		for (int i = 0; i < 2; i++) {
			int imgType = (i == 0 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_4BYTE_ABGR);
			for (int bpc = 1; bpc < 9; bpc++) {
				BufferedImage img = new BufferedImage(1000, 1000, imgType);
				byte[] data = genRandData(1 << 13);

				Encoder ie = new ImgEncoder(img, bpc);
				ie.encodeBits(BitByteConv.intToBitArray(data.length, 32));
				ie.encodeBytes(data);
				ie.stopThreads();

				Decoder id = new ImgDecoder(img);
				byte[] fSizeBits = id.readBits(32);
				int bytesToRead = BitByteConv.bitArrayToInt(fSizeBits, false);

				byte[] decData = id.readBytes(bytesToRead);

				assertArrayEquals(data, decData);

				System.out.println("Passed bpc " + bpc + " for " + (imgType == BufferedImage.TYPE_4BYTE_ABGR ? "A" : "") + "RGB encoding/decoding");
			}
			System.out.println();
		}
	}

	@Test
	public void testAudEncDec() {
		byte[] audData = genRandData(1 << 18); // 262 kb, enough for all bpc to not run out of space in aud file
		AudioFormat af = new AudioFormat(44100, 16, 2, true, false);

		for (int bpc = 1; bpc < 9; bpc++) {
			AudioInputStream sampleAudio = new AudioInputStream(new ByteArrayInputStream(audData), af, audData.length);

			byte[] data = genRandData(1 << 13);
			Encoder ae = new AudioEncoder(sampleAudio, bpc);
			ae.encodeBits(BitByteConv.intToBitArray(data.length, 32));
			ae.encodeBytes(data);
			ae.stopThreads();

			AudioFormat f = new AudioFormat(sampleAudio.getFormat().getSampleRate(), 16,
					sampleAudio.getFormat().getChannels(), true, false
			);

			byte[] encData = ((AudioEncoder) ae).getEncodedPCM();
			Decoder ad = new AudioDecoder(new AudioInputStream(new ByteArrayInputStream(encData), f, encData.length));

			byte[] fSizeBits = ad.readBits(32);
			byte[] decData = ad.readBytes(BitByteConv.bitArrayToInt(fSizeBits, false));

			assertArrayEquals(data, decData);

			System.out.println("Passed bpc " + bpc + " for audio encoding/decoding");
		}
		System.out.println();
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
