package nsteg.nsteg_utils;

import nsteg.encoders.Encoder;

public enum FileType {
	IMAGE, AUDIO, UNKNOWN;

	/**
	 * Determines the file type based on the file extension. Returns IMAGE if the file is of a type that the ImgEncoder
	 * or ImgDecoder will know what to do with, AUDIO if the file is of a type that the AudEncoder or AudDecoder will
	 * know what to do with, or UNKNOWN if the file is one that nsteg cannot work with.
	 *
	 * @param fileExt File extension to check for file type
	 * @return FileType of the given file extension
	 */
	public static FileType getFileType(String fileExt) {
		if (Encoder.inImgFormats.contains(fileExt) || Encoder.outImgFormats.contains(fileExt))
			return IMAGE;
		else if (Encoder.inAudFormats.contains(fileExt) || Encoder.outAudFormats.contains(fileExt))
			return AUDIO;
		else
			return UNKNOWN;
	}
}
