# nstag

nstag is a steganography tool that helps you hide files inside images, by inserting the bits of the file you wish to hide into the least significant bit(s) of the ARGB channels of each pixel in the image. nstag also currently supports AES 128 bit encryption, in order to protect your data in the event someone tried to decode the information held in the image.

### Installation

Just download one of the jar files, or compile your own from the release version of the source code, if you so wish.

### Use

Simply run the program as you would any other jar file, from the command line (e.g "java -jar nstag-vX.Y.jar"). Memory parameters are unnecessary, but do keep in mind that the program needs around 1GB of RAM at the present time.

When using image encoding, the number of bits per channel are the number of least significant bits to use in each channel, in each pixel, to encode the file. Since each value is an unsigned byte, you can have a minimum of 1, and a maximum of 8 for this value. Higher values mean more data can be encoded, but the image will look less like the original.
