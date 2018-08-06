# nsteg

nsteg is a steganography tool that helps you hide files inside images or audio files. nsteg also currently supports AES-256 bit encryption, in order to protect your data in the event someone tried to decode the information held in the image/audio file.

## Installation:

Just download one of the jar files, or compile your own from the release version of the source code, if you so wish.

## Use:

Simply run the program as you would any other jar file, from the command line (e.g "java -jar nsteg-vX.Y-release.jar"), to use the interactive mode.
To run in non-interactive mode, run the jar file with the "--help" flag. It will display detailed documentation on how to utilize the non-interactive mode.

When using image encoding, the number of bits per channel are the number of least significant bits to use in each channel, in each pixel, to encode the file. Since each value is an unsigned byte, you can have a minimum of 1, and a maximum of 8 for this value. Higher values mean more data can be encoded, but the image will look less like the original. A value of 1 or 2 is recommended.

When using audio encoding, the number of bits per channel are the number of least significant bits to use in the right channel of the audio file. The left channel must be left intact, or the distortion becomes very audible. The recommended range is 1-4, although the absolute minimum is 1 and the absolute maximum is 8.

## How it works

Image encoding/decoding: [https://github.com/nanoandrew4/nsteg/blob/master/IMG_ENC.md]

Audio encoding/decoding: [https://github.com/nanoandrew4/nsteg/blob/master/AUD_ENC.md]

#### Encoding format

This is the encoding format nsteg uses. For a more specific overview, check out the Encoder class, which lays out the spec in the class docs. The encode() method in the Encoder class should also be fairly readable, for a full understanding of the encoding order.

![Encoding format](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/encformat.png)

#### Encoding and decoding high level overview

![HL overview](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/encdec.png)
