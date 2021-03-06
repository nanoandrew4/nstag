package nsteg.encoders.aud;

/**
 * Used for returning data when reading FLAC files to the AudEncoder class.
 *
 * @see nsteg.processors.AudioProcessor
 */
public class FLACData {
	public byte[] pcm;
	public int sampleRate, bitsPerSample, channels;
}
