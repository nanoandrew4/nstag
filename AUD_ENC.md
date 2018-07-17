# Audio encoding
Audio files contain PCM data, which may be compressed and mangled, but it is always possible to extract PCM data from an audio file. This PCM data is stored in memory as an array of bytes, where each byte corresponds to the output for a specific channel. Most audio files nowadays are stereo, meaning they have two channels (left ear, right ear). 

![Sample bytes](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/sample.png)

The left channel must be left untouched, or the distortion caused by modifying it, even the tiniest bit, is very audible. Therefore the only channel that can be modified is the right one. Thankfully, 3-4 bits can be used in each of the right channel bytes without affecting the quality of the sound much. As with image encoding, the bits from the file are inserted in the least significant bits of the right channel bytes, from right to left. This is done by disassembling the right channel bytes into bits, modifying the necessary least significant bits, reassembling the byte with the modified bits, and overwriting its old value.

![Least significant bit](https://raw.githubusercontent.com/nanoandrew4/nsteg/master/readme_res/lsb.png)
