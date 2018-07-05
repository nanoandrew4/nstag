# nsteg

nsteg is a steganography tool that helps you hide files inside images, by inserting the bits of the file you wish to hide into the least significant bit(s) of the (A)RGB channels of each pixel in the image. nsteg also currently supports AES-256 bit encryption, in order to protect your data in the event someone tried to decode the information held in the image.

## Installation:

Just download one of the jar files, or compile your own from the release version of the source code, if you so wish.

## Use:

Simply run the program as you would any other jar file, from the command line (e.g "java -jar nsteg-vX.Y.jar").

When using image encoding, the number of bits per channel are the number of least significant bits to use in each channel, in each pixel, to encode the file. Since each value is an unsigned byte, you can have a minimum of 1, and a maximum of 8 for this value. Higher values mean more data can be encoded, but the image will look less like the original.

## How it works:
---
#### Encoding and decoding
Each pixel in the image is composed of three or four channels, depending on the image type. The four channel types are alpha, red, green and blue. The first does not exist on images with three channels (which contain the RGB channels), and does exist on images with four channels (ARGB). The alpha channel controls the transparency of the pixel.

![Channel bytes](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/chan.png)

Each channel has 1 byte, and in Java, they are strung together to form an int (which is 4 bytes long, or 32 bits). Even though some images have no alpha channel, each pixel still has 4 bytes, the alpha byte is just always 255 (opaque) and never gets written to the image.

In order to store information, we have to separate the channels, to end up with three (or four) individual bytes. Once we have a byte, we split it into its individual bits. This is the level we will be operating on, since we can only store bits from the file in order to cause imperceptible changes to the image.

![Least significant bit](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/lsb.png)

What we do to store the data is take one bit at a time from the file, and encode it in the least significant bit. At most, we are causing a 1/255 of a change in color, which even upon close inspection is not noticeable, even having the original side by side. nsteg also supports writing to more than one least significant bit, but doing so causes more of a visual change. In doing so, instead of only writing to one bit, you write to the last two bits, or however many you wish, up to 8.

And that, in a nutshell, is the encoding process! Decoding is just in reverse, we read the least significant bit(s) and use the bits to make the bytes that formed the original file. 

#### Encoding format

This is the encoding format nsteg uses. 

The first 4 bits written specify how many least significant bits were used. The next 32 bits specify the compressed size of the file that was encoded. The next 32 bits specify the uncompressed size of the file that was encoded. If encryption was used, the next 64 bits correspond to the salt which was used to hash the user input password and derive the key that encrypted the data. The remaining bits correspond to the file, and if it was encrypted, the initialization vector and additional associated data are bundled with it, for decryption later.

![Encoding format](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/encformat.png)

#### Encoding and decoding high level overview

![HL overview](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/encdec.png)

---
