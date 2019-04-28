#!/system/bin/sh
# Run a loopback test in the background after a delay.
# To run the script enter:
#    adb shell "nohup sh /data/loopback.sh &"

SLEEP_TIME=10
TEST_COMMAND="aaudio_loopback -pl -Pl -C1 -n2 -m2 -tm -d5"

echo "Plug in USB Mir and Fun Plug."
echo "Test will start in ${SLEEP_TIME} seconds: ${TEST_COMMAND}"
sleep ${SLEEP_TIME}
date > /data/loopreport.txt
${TEST_COMMAND} >> /data/loopreport.txt
date >> /data/loopreport.txt
