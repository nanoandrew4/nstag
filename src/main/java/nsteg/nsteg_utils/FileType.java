package nsteg.nsteg_utils;

import nsteg.encoders.Encoder;

public enum FileType {
	IMAGE, AUDIO, UNKNOWN;

	public static FileType getFileType(String fileExt) {
		if (Encoder.inImgFormats.contains(fileExt) || Encoder.outImgFormats.contains(fileExt))
			return IMAGE;
		else if (Encoder.inAudFormats.contains(fileExt) || Encoder.outAudFormats.contains(fileExt))
			return AUDIO;
		else
			return UNKNOWN;
	}
}
