## 3.7\. Runtime Compatibility

Device implementations:

*    [C-0-1] MUST support the full Dalvik Executable (DEX) format
and [Dalvik bytecode specification and semantics](https://android.googlesource.com/platform/dalvik/).

*    [C-0-2] MUST configure Dalvik runtimes to allocate memory in
accordance with the upstream Android platform, and as specified by
the following table. (See [section 7.1.1](#7_1_1_screen_configuration) for
screen size and screen density definitions.)

*    SHOULD use Android RunTime (ART), the reference upstream
implementation of the Dalvik Executable Format, and the reference
implementation’s package management system.

*    SHOULD run fuzz tests under various modes of execution
and target architectures to assure the stability of the runtime. Refer to
[JFuzz](https://android.googlesource.com/platform/art/+/master/tools/dexfuzz/)
and [DexFuzz](https://android.googlesource.com/platform/art/+/master/tools/dexfuzz/)
in the Android Open Source Project website.

Note that memory values specified below are considered minimum values and
device implementations MAY allocate more memory per application.

<table>
 <tr>
    <th>Screen Layout</th>
    <th>Screen Density</th>
    <th>Minimum Application Memory</th>
 </tr>
 <tr>
    <td rowspan="12">Android Watch</td>
    <td>120 dpi (ldpi)</td>
    <td rowspan="3">32MB</td>
 </tr>
 <tr>
    <td>160 dpi (mdpi)</td>
 </tr>
 <tr>
    <td>213 dpi (tvdpi)</td>
 </tr>
 <tr>
    <td>240 dpi (hdpi)</td>
    <td rowspan="2">36MB</td>
 </tr>
 <tr>
    <td>280 dpi (280dpi)</td>
 </tr>
 <tr>
    <td>320 dpi (xhdpi)</td>
    <td rowspan="2">48MB</td>
 </tr>
 <tr>
    <td>360 dpi (360dpi)</td>
 </tr>
 <tr>
    <td>400 dpi (400dpi)</td>
    <td>56MB</td>
 </tr>
 <tr>
    <td>420 dpi (420dpi)</td>
    <td>64MB</td>
 </tr>
 <tr>
    <td>480 dpi (xxhdpi)</td>
    <td>88MB</td>
 </tr>
 <tr>
    <td>560 dpi (560dpi)</td>
    <td>112MB</td>
 </tr>
 <tr>
    <td>640 dpi (xxxhdpi)</td>
    <td>154MB</td>
 </tr>
 <tr>
    <td rowspan="12">small/normal</td>
    <td>120 dpi (ldpi)</td>
    <td rowspan="2">32MB</td>
 </tr>
 <tr>
    <td>160 dpi (mdpi)</td>
 </tr>
 <tr>
    <td>213 dpi (tvdpi)</td>
    <td rowspan="3">48MB</td>
 </tr>
 <tr>
    <td>240 dpi (hdpi)</td>
 </tr>
 <tr>
    <td>280 dpi (280dpi)</td>
 </tr>
 <tr>
    <td>320 dpi (xhdpi)</td>
    <td rowspan="2">80MB</td>
 </tr>
 <tr>
    <td>360 dpi (360dpi)</td>
 </tr>
 <tr>
    <td>400 dpi (400dpi)</td>
    <td>96MB</td>
 </tr>
 <tr>
    <td>420 dpi (420dpi)</td>
    <td>112MB</td>
 </tr>
 <tr>
    <td>480 dpi (xxhdpi)</td>
    <td>128MB</td>
 </tr>
 <tr>
    <td>560 dpi (560dpi)</td>
    <td>192MB</td>
 </tr>
 <tr>
    <td>640 dpi (xxxhdpi)</td>
    <td>256MB</td>
 </tr>
 <tr>
    <td rowspan="12">large</td>
    <td>120 dpi (ldpi)</td>
    <td>32MB</td>
 </tr>
 <tr>
    <td>160 dpi (mdpi)</td>
    <td>48MB</td>
 </tr>
 <tr>
    <td>213 dpi (tvdpi)</td>
    <td rowspan="2">80MB</td>
 </tr>
 <tr>
    <td>240 dpi (hdpi)</td>
 </tr>
 <tr>
    <td>280 dpi (280dpi)</td>
    <td>96MB</td>
 </tr>
 <tr>
    <td>320 dpi (xhdpi)</td>
    <td>128MB</td>
 </tr>
 <tr>
    <td>360 dpi (360dpi)</td>
    <td>160MB</td>
 </tr>
 <tr>
    <td>400 dpi (400dpi)</td>
    <td>192MB</td>
 </tr>
 <tr>
    <td>420 dpi (420dpi)</td>
    <td>228MB</td>
 </tr>
 <tr>
    <td>480 dpi (xxhdpi)</td>
    <td>256MB</td>
 </tr>
 <tr>
    <td>560 dpi (560dpi)</td>
    <td>384MB</td>
 </tr>
 <tr>
    <td>640 dpi (xxxhdpi)</td>
    <td>512MB</td>
 </tr>
 <tr>
    <td rowspan="12">xlarge</td>
    <td>120 dpi (ldpi)</td>
    <td>48MB</td>
 </tr>
 <tr>
    <td>160 dpi (mdpi)</td>
    <td>80MB</td>
 </tr>
 <tr>
    <td>213 dpi (tvdpi)</td>
    <td rowspan="2">96MB</td>
 </tr>
 <tr>
    <td>240 dpi (hdpi)</td>
 </tr>
 <tr>
    <td>280 dpi (280dpi)</td>
    <td>144MB</td>
 </tr>
 <tr>
    <td>320 dpi (xhdpi)</td>
    <td>192MB</td>
 </tr>
 <tr>
    <td>360 dpi (360dpi)</td>
    <td>240MB</td>
 </tr>
 <tr>
    <td>400 dpi (400dpi)</td>
    <td>288MB</td>
 </tr>
 <tr>
    <td>420 dpi (420dpi)</td>
    <td>336MB</td>
 </tr>
 <tr>
    <td>480 dpi (xxhdpi)</td>
    <td>384MB</td>
 </tr>
 <tr>
    <td>560 dpi (560dpi)</td>
    <td>576MB</td>
 </tr>
 <tr>
    <td>640 dpi (xxxhdpi)</td>
    <td>768MB</td>
 </tr>
</table>
