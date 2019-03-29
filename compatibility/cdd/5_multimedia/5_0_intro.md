# 5\. Multimedia Compatibility

Device implementations:

*   [C-0-1] MUST support the media formats, encoders, decoders, file types,
    and container formats defined in [section 5.1](#5_1_media-codecs.md)
    for each and every codec declared by `MediaCodecList`.
*   [C-0-2] MUST declare and report support of the encoders, decoders available
    to third-party applications via [`MediaCodecList`](
    http://developer.android.com/reference/android/media/MediaCodecList.html).
*   [C-0-3] MUST be able to decode and make available to third-party apps all
    the formats it can encode. This includes all bitstreams that its encoders
    generate and the profiles reported in its [`CamcorderProfile`](
    http://developer.android.com/reference/android/media/CamcorderProfile.html).


Device implementations:

*   SHOULD aim for minimum codec latency, in others words, they
    *   SHOULD NOT consume and store input buffers and return input buffers only
    once processed.
    *   SHOULD NOT hold onto decoded buffers for longer than as specified by the
    standard (e.g. SPS).
    *   SHOULD NOT hold onto encoded buffers longer than required by the GOP
    structure.

All of the codecs listed in the section below are provided as software
implementations in the preferred Android implementation from the Android Open
Source Project.

Please note that neither Google nor the Open Handset Alliance make any
representation that these codecs are free from third-party patents. Those
intending to use this source code in hardware or software products are advised
that implementations of this code, including in open source software or
shareware, may require patent licenses from the relevant patent holders.
