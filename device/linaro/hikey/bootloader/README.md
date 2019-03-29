Bootloader is build with ArmTF and UEFI from sources located at:
  device/linaro/bootloader
To build fip.bin and l-loader.bin do:
  $ cd device/linaro/hikey/bootloader
  $ make
Results will be in out/dist

We can also generate ptable (needs root privilege) with below commands:
  $ cd device/linaro/hikey/l-loader/
  $ make ptable.img
