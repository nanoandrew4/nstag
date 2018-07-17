# Image encoding:
Each pixel in the image is composed of three or four channels, depending on the image type. The four channel types are alpha, red, green and blue. The first does not exist on images with three channels (which contain the RGB channels), and does exist on images with four channels (ARGB). The alpha channel controls the transparency of the pixel.

![Channel bytes](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/chan.png)

Each channel has 1 byte, and in Java, they are strung together to form an int (which is 4 bytes long, or 32 bits). Even though some images have no alpha channel, each pixel still has 4 bytes, the alpha byte is just always 255 (opaque) and never gets written to the image.

In order to store information, we have to separate the channels, to end up with three (or four) individual bytes. Once we have a byte, we split it into its individual bits. This is the level we will be operating on, since we can only store bits from the file in order to cause imperceptible changes to the image.

![Least significant bit](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/lsb.png)

What we do to store the data is take one bit at a time from the file, and encode it in the least significant bit. At most, we are causing a 1/255 of a change in color, which even upon close inspection is not noticeable, even having the original side by side. nsteg also supports writing to more than one least significant bit, but doing so causes more of a visual change. In doing so, instead of only writing to one bit, you write to the last two bits, or however many you wish, up to 8.

And that, in a nutshell, is the encoding process! Decoding is just in reverse, we read the least significant bit(s) and use the bits to make the bytes that formed the original file.
