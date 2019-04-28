package  com.android.tv.testing.testinput;


import com.android.tv.testing.testinput.ChannelStateData;

/** Remote interface for controlling the test TV Input Service */
interface ITestInputControl {
    void updateChannelState(int origId, in ChannelStateData data);
}