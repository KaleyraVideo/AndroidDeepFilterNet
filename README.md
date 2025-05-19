[![Release Version](https://img.shields.io/maven-central/v/io.github.kaleyravideo/android-deepfilternet?color=0881BE)](https://central.sonatype.com/artifact/io.github.kaleyravideo/android-deepfilternet)
[![Android CI](https://github.com/KaleyraVideo/AndroidDeepFilterNet/actions/workflows/tests.yml/badge.svg?branch=main)](https://github.com/KaleyraVideo/AndroidDeepFilterNet/actions/workflows/tests.yml)
# Android DeepFilterNet

This repository provides an Android implementation of the DeepFilterNet noise suppression model with JNI bindings.

## Overview

DeepFilterNet is a state-of-the-art neural network architecture designed for real-time noise suppression in audio streams. This Android implementation allows developers to integrate high-quality noise removal capabilities into Android applications.

## Features

- Real-time noise suppression for Android applications
- JNI bindings for efficient integration with native Android code
- Optimized for mobile performance

## Requirements

- Android API level 21+ (Android 5.0 or higher)
- Android NDK r21+
- Gradle 7.0+

## Installation

Add the following to your app's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.kaleyra.video:deepfilternet:x.y.z'
}
```

## Usage

### Basic Usage

```kotlin
// Initialize the DeepFilterNet instance
val deepFilterNet = com.rikorose.deepfilternet.NativeDeepFilterNet(context)

// Set the noise attenuation level (dB)
deepFilterNet.setAttenuationLimit(30f)

// Retrieve the audio byte buffer according to your implementation;
// this example uses a callback.
val bufferCallback = object: YourBufferCallback { byteBuffer ->
    // Call the 'processFrame' function on the DeepFilterNet object,
    // passing the received 'byteBuffer' as an argument.
    deepFilterNet.processFrame(byteBuffer)
}

// Release resources when done
deepFilterNet.release()
```

## Documentation

For detailed documentation on the API, see the [documentation](https://html-preview.github.io/?url=https://github.com/KaleyraVideo/AndroidDeepFilterNet/blob/main/noise-filter/doc/index.html).

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Credits

This project is built upon a [fork](https://github.com/KaleyraVideo/DeepFilterNet) of the original [DeepFilterNet](https://github.com/rikorose/DeepFilterNet) work by Hendrik Schröter.<br/>
Further information regarding the optimization process of the DeepFilterNet model for running on a mobile device is available in this [document](https://github.com/KaleyraVideo/DeepFilterNet/blob/main/models/deepfilternet_model_optimization.md).

## Contact

For questions or support, please open an issue on the GitHub repository or contact the Kaleyra Video team at cis-eu.video.engineering@tatacommunications.com.
